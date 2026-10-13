package com.benchreadiness.interview.service;

import com.benchreadiness.interview.branch.BranchContext;
import com.benchreadiness.interview.dto.BulkCreateInterviewRequest;
import com.benchreadiness.interview.dto.BulkCreateInterviewResult;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.org.OrgContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Fix 6.14: bulk create runs on bulkInterviewExecutor, where the request-thread OrgContext and
 * BranchContext are absent. Before the fix every bulk interview was stored as TESTYANTRA
 * (seen live: 395 KES-admin bulk interviews filed under TESTYANTRA, 11-13 Oct).
 */
class BulkInterviewOrgContextTest {

    // One worker thread, so the second task reuses the first one's thread (checks for leaks).
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        OrgContext.clear();
        BranchContext.clear();
        pool.shutdownNow();
    }

    @Test
    void bulkWorkersSeeCallerOrgAndBranchAndDoNotLeakThem() throws Exception {
        InterviewService service = spy(new InterviewService(null, null, null, null, null, null,
            null, null, null, null, null, null, pool));
        Map<String, String> seenOrg = new ConcurrentHashMap<>();
        Map<String, String> seenBranch = new ConcurrentHashMap<>();
        doAnswer(inv -> {
            String email = ((com.benchreadiness.interview.dto.CreateInterviewRequest) inv.getArgument(0))
                .getEngineerEmail();
            seenOrg.put(email, String.valueOf(OrgContext.getOrDefault()));
            seenBranch.put(email, String.valueOf(BranchContext.get()));
            Interview created = mock(Interview.class);
            when(created.getId()).thenReturn("iv-" + email);
            return created;
        }).when(service).createInterview(any(), any(), any(), any());

        BranchInterviewValidator validator = mock(BranchInterviewValidator.class);
        when(validator.validateAndResolveBranch(any(), any(), any())).thenReturn("TESTING");

        // Request thread, as set by HeaderAuthenticationFilter for a KES admin.
        OrgContext.set("KES");
        BranchContext.set("TESTING");

        BulkCreateInterviewResult result = service.createBulkInterviews(
            request("a@example.com", "b@example.com"), "admin-1", "ADMIN", validator);

        assertThat(result.getResults()).allMatch(r -> "OK".equals(r.getStatus()));
        assertThat(seenOrg).containsEntry("a@example.com", "KES").containsEntry("b@example.com", "KES");
        assertThat(seenBranch).containsEntry("a@example.com", "TESTING").containsEntry("b@example.com", "TESTING");

        // The reused worker thread must not keep the caller's org/branch for the next task.
        String[] after = pool.submit(() -> new String[] { OrgContext.get(), BranchContext.get() }).get();
        assertThat(after[0]).isNull();
        assertThat(after[1]).isNull();
    }

    private static BulkCreateInterviewRequest request(String... emails) {
        BulkCreateInterviewRequest req = new BulkCreateInterviewRequest();
        req.setJdTitle("Java Backend Developer");
        req.setCandidates(java.util.Arrays.stream(emails).map(e -> {
            BulkCreateInterviewRequest.CandidateEntry c = new BulkCreateInterviewRequest.CandidateEntry();
            c.setEngineerEmail(e);
            c.setEngineerName("Candidate");
            return c;
        }).toList());
        return req;
    }
}
