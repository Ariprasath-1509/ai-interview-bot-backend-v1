package com.benchreadiness.ai.controller;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.benchreadiness.ai.client.InterviewServiceClient;
import com.benchreadiness.ai.config.AsyncConfig;
import com.benchreadiness.ai.dto.AssessmentRequest;
import com.benchreadiness.ai.service.AssessmentService;
import com.benchreadiness.ai.service.AsyncAssessmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fix 6.1: POST /assess-async through the real @Async proxy and assessmentExecutor.
 * Pool 1 / queue 0, so a second concurrent run is rejected.
 */
class AssessAsyncRejectionTest {

    private final AssessmentService assessmentService = mock(AssessmentService.class);
    private final InterviewServiceClient interviewServiceClient = mock(InterviewServiceClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(AsyncConfig.class)
        .withPropertyValues(
            "app.assessment.core-pool-size=1",
            "app.assessment.thread-pool-size=1",
            "app.assessment.queue-capacity=0")
        .withBean(AssessmentService.class, () -> assessmentService)
        .withBean(ComplianceServiceClient.class, () -> mock(ComplianceServiceClient.class))
        .withBean(InterviewServiceClient.class, () -> interviewServiceClient)
        .withBean(ObjectMapper.class, () -> objectMapper)
        .withBean(AsyncAssessmentService.class);

    @Test
    void happyPathUnchangedAndRejectionBecomesFailedWith503() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        when(assessmentService.assess(any(), any())).thenAnswer(inv -> {
            started.countDown();
            release.await(10, TimeUnit.SECONDS);
            return Map.of("proposedVerdict", "PASS");
        });

        runner.run(ctx -> {
            AsyncAssessmentService async = ctx.getBean(AsyncAssessmentService.class);
            AiController controller = new AiController(null, assessmentService, async, null, null, null,
                null, null, null, null, null, objectMapper);

            // Happy path: accepted -> 200 ASSESSMENT_PENDING, same body as before.
            ResponseEntity<?> ok = controller.assessAsync(request("iv-a"), "user-1");
            assertThat(ok.getStatusCode().value()).isEqualTo(200);
            Map<String, Object> okBody = castBody(ok);
            assertThat(okBody.keySet()).containsExactlyInAnyOrder("status", "message", "interviewId", "runId");
            assertThat(okBody.get("status")).isEqualTo("ASSESSMENT_PENDING");
            String runA = (String) okBody.get("runId");
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            // Pool busy, queue 0 -> rejected -> 503 + Retry-After, run FAILED (QUEUE_FULL), not PROCESSING.
            ResponseEntity<?> rejected = controller.assessAsync(request("iv-b"), "user-1");
            assertThat(rejected.getStatusCode().value()).isEqualTo(503);
            assertThat(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .isEqualTo(String.valueOf(AiController.ASSESS_QUEUE_FULL_RETRY_AFTER_SECONDS));
            Map<String, Object> body = castBody(rejected);
            assertThat(body.get("status")).isEqualTo("FAILED");
            assertThat(body.get("error")).isEqualTo(AsyncAssessmentService.REASON_QUEUE_FULL);
            String runB = (String) body.get("runId");

            AsyncAssessmentService.AssessmentStatus statusB = async.getAssessmentStatus("iv-b");
            assertThat(statusB.getStatus()).isEqualTo("FAILED");
            assertThat(statusB.getError()).isEqualTo(AsyncAssessmentService.REASON_QUEUE_FULL);
            assertThat(statusB.getRunId()).isEqualTo(runB);
            verify(interviewServiceClient).updateAssessmentStatus(eq("iv-b"), argThat(m ->
                "FAILED".equals(m.get("status")) && "QUEUE_FULL".equals(m.get("error")) && runB.equals(m.get("runId"))));

            // The poll endpoint reports FAILED for the rejected run.
            Map<String, Object> polled = castBody(controller.getAssessmentStatus("iv-b", runB));
            assertThat(polled.get("status")).isEqualTo("FAILED");

            // The accepted run still completes normally.
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!"COMPLETED".equals(async.getAssessmentStatus("iv-a").getStatus()) && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(async.getAssessmentStatus("iv-a").getStatus()).isEqualTo("COMPLETED");
            assertThat(async.getAssessmentStatus("iv-a").getRunId()).isEqualTo(runA);
            verify(interviewServiceClient).updateAssessmentStatus(eq("iv-a"), argThat(m ->
                "COMPLETED".equals(m.get("status")) && runA.equals(m.get("runId"))));
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castBody(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    private static AssessmentRequest request(String interviewId) {
        AssessmentRequest req = new AssessmentRequest();
        req.setInterviewId(interviewId);
        return req;
    }
}
