package com.benchreadiness.interview.service;

import com.benchreadiness.interview.branch.BranchContext;
import com.benchreadiness.interview.dto.BulkCreateInterviewRequest;
import com.benchreadiness.interview.dto.BulkCreateInterviewResult;
import com.benchreadiness.interview.dto.CreateInterviewRequest;
import com.benchreadiness.interview.entity.Engineer;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.InterviewStatus;
import com.benchreadiness.interview.org.OrgContext;
import com.benchreadiness.interview.repository.EngineerRepository;
import com.benchreadiness.interview.repository.InterviewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fix 60: a retried bulk batch must not create a second interview for the same engineer and slot. */
class BulkInterviewDuplicateCheckTest {

    private static final Instant SLOT = Instant.parse("2026-10-20T10:00:00Z");

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private InterviewRepository interviewRepository;
    private EngineerRepository engineerRepository;
    private InterviewService service;
    private BranchInterviewValidator validator;
    private final AtomicInteger created = new AtomicInteger();
    private final Map<String, String> seenOrg = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        interviewRepository = mock(InterviewRepository.class);
        engineerRepository = mock(EngineerRepository.class);
        service = spy(new InterviewService(interviewRepository, engineerRepository, null, null, null, null,
            null, null, null, null, null, null, pool));
        doAnswer(inv -> {
            String email = ((CreateInterviewRequest) inv.getArgument(0)).getEngineerEmail();
            seenOrg.put(email, String.valueOf(OrgContext.getOrDefault()) + "/" + BranchContext.get());
            created.incrementAndGet();
            Interview iv = mock(Interview.class);
            when(iv.getId()).thenReturn("new-" + email);
            return iv;
        }).when(service).createInterview(any(), any(), any(), any());
        validator = mock(BranchInterviewValidator.class);
        when(validator.validateAndResolveBranch(any(), any(), any())).thenReturn("TESTING");
        OrgContext.set("KES");
        BranchContext.set("TESTING");
    }

    @AfterEach
    void tearDown() {
        OrgContext.clear();
        BranchContext.clear();
        pool.shutdownNow();
    }

    @Test
    void existingLiveInterviewIsSkippedWithItsId() {
        givenEngineer("a@example.com", "eng-a");
        givenExisting("eng-a", "iv-old", null);

        BulkCreateInterviewResult result = run(SLOT, null, "a@example.com", "b@example.com");

        assertThat(result.getResults().get(0).getStatus()).isEqualTo("FAILED");
        assertThat(result.getResults().get(0).getError())
            .isEqualTo("Already scheduled for this time (interview iv-old)");
        assertThat(result.getResults().get(1).getStatus()).isEqualTo("OK");
        assertThat(created.get()).isEqualTo(1);
    }

    @Test
    void queryUsesCallerOrgSlotAndOnlyLiveStatuses() {
        givenEngineer("a@example.com", "eng-a");

        run(SLOT, null, "a@example.com");

        verify(interviewRepository).findByEngineerIdAndOrgCodeAndScheduledAtAndStatusInOrderByCreatedAtDesc(
            eq("eng-a"), eq("KES"), eq(SLOT), eq(InterviewService.LIVE_STATUSES));
        assertThat(InterviewService.LIVE_STATUSES)
            .doesNotContain(InterviewStatus.EXPIRED, InterviewStatus.WITHDRAWN)
            .containsExactlyInAnyOrder(InterviewStatus.DRAFT, InterviewStatus.SCHEDULED,
                InterviewStatus.IN_PROGRESS, InterviewStatus.REVIEW_PENDING,
                InterviewStatus.COMPLETED, InterviewStatus.SIGNED_OFF);
    }

    @Test
    void expiredAndWithdrawnAreNotDuplicates() {
        givenEngineer("a@example.com", "eng-a");
        // The repository only returns rows in LIVE_STATUSES, so an EXPIRED/WITHDRAWN row never comes back.
        when(interviewRepository.findByEngineerIdAndOrgCodeAndScheduledAtAndStatusInOrderByCreatedAtDesc(
            any(), any(), any(), anyCollection())).thenAnswer(inv -> {
                Collection<InterviewStatus> asked = inv.getArgument(3);
                assertThat(asked).doesNotContain(InterviewStatus.EXPIRED, InterviewStatus.WITHDRAWN);
                return List.of();
            });

        BulkCreateInterviewResult result = run(SLOT, null, "a@example.com");

        assertThat(result.getResults().get(0).getStatus()).isEqualTo("OK");
        assertThat(created.get()).isEqualTo(1);
    }

    @Test
    void differentScheduledAtIsAllowed() {
        givenEngineer("a@example.com", "eng-a");
        Instant other = SLOT.plusSeconds(3600);
        // An interview exists only at SLOT; a lookup for any other time finds nothing.
        List<Interview> atSlot = List.of(existing("iv-old", null));
        when(interviewRepository.findByEngineerIdAndOrgCodeAndScheduledAtAndStatusInOrderByCreatedAtDesc(
            any(), any(), eq(SLOT), anyCollection())).thenReturn(atSlot);

        BulkCreateInterviewResult result = run(other, null, "a@example.com");

        assertThat(result.getResults().get(0).getStatus()).isEqualTo("OK");
        assertThat(created.get()).isEqualTo(1);
    }

    @Test
    void roundNameDecidesWhenBothAreSet() {
        givenEngineer("a@example.com", "eng-a");
        givenExisting("eng-a", "iv-r1", "Round 1");

        assertThat(run(SLOT, "Round 2", "a@example.com").getResults().get(0).getStatus()).isEqualTo("OK");
        assertThat(run(SLOT, "round 1", "a@example.com").getResults().get(0).getStatus()).isEqualTo("FAILED");
        // No round on the request: slot match alone counts.
        assertThat(run(SLOT, null, "a@example.com").getResults().get(0).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void sameEmailTwiceInOneBatchCreatesOnlyOne() {
        givenEngineer("a@example.com", "eng-a");

        BulkCreateInterviewResult result = run(SLOT, null, "a@example.com", "A@Example.com ", "a@example.com");

        long ok = result.getResults().stream().filter(r -> "OK".equals(r.getStatus())).count();
        long failed = result.getResults().stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        assertThat(ok).isEqualTo(1);
        assertThat(failed).isEqualTo(2);
        assertThat(created.get()).isEqualTo(1);
        assertThat(result.getResults()).filteredOn(r -> "FAILED".equals(r.getStatus()))
            .allMatch(r -> r.getError().startsWith("Duplicate email in this upload"));
    }

    @Test
    void newEngineerHasNoDuplicateAndSkipsTheInterviewQuery() {
        when(engineerRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        BulkCreateInterviewResult result = run(SLOT, null, "new@example.com");

        assertThat(result.getResults().get(0).getStatus()).isEqualTo("OK");
        verify(interviewRepository, never())
            .findByEngineerIdAndOrgCodeAndScheduledAtAndStatusInOrderByCreatedAtDesc(any(), any(), any(), any());
    }

    @Test
    void noScheduledAtMeansNoDuplicateCheck() {
        BulkCreateInterviewResult result = run(null, null, "a@example.com");

        assertThat(result.getResults().get(0).getStatus()).isEqualTo("OK");
        verify(engineerRepository, never()).findByEmail(any());
    }

    @Test
    void orgAndBranchStayCorrectOnWorkerThreadsWithTheCheckOn() {
        givenEngineer("a@example.com", "eng-a");
        givenEngineer("b@example.com", "eng-b");

        run(SLOT, null, "a@example.com", "b@example.com");

        assertThat(seenOrg).containsEntry("a@example.com", "KES/TESTING").containsEntry("b@example.com", "KES/TESTING");
    }

    // ---- helpers ----

    private void givenEngineer(String email, String id) {
        Engineer e = mock(Engineer.class);
        when(e.getId()).thenReturn(id);
        when(engineerRepository.findByEmail(email)).thenReturn(Optional.of(e));
        when(interviewRepository.findByEngineerIdAndOrgCodeAndScheduledAtAndStatusInOrderByCreatedAtDesc(
            eq(id), any(), any(), anyCollection())).thenReturn(List.of());
    }

    private void givenExisting(String engineerId, String interviewId, String round) {
        List<Interview> rows = List.of(existing(interviewId, round));
        when(interviewRepository.findByEngineerIdAndOrgCodeAndScheduledAtAndStatusInOrderByCreatedAtDesc(
            eq(engineerId), eq("KES"), eq(SLOT), anyCollection())).thenReturn(rows);
    }

    private static Interview existing(String id, String round) {
        Interview iv = mock(Interview.class);
        when(iv.getId()).thenReturn(id);
        when(iv.getRoundName()).thenReturn(round);
        return iv;
    }

    private BulkCreateInterviewResult run(Instant scheduledAt, String round, String... emails) {
        created.set(0);
        BulkCreateInterviewRequest req = new BulkCreateInterviewRequest();
        req.setJdTitle("Java Backend Developer");
        req.setScheduledAt(scheduledAt);
        req.setRoundName(round);
        req.setCandidates(Arrays.stream(emails).map(e -> {
            BulkCreateInterviewRequest.CandidateEntry c = new BulkCreateInterviewRequest.CandidateEntry();
            c.setEngineerEmail(e);
            c.setEngineerName("Candidate");
            return c;
        }).toList());
        return service.createBulkInterviews(req, "admin-1", "ADMIN", validator);
    }
}
