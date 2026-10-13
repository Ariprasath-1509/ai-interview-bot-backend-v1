package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.benchreadiness.ai.client.InterviewServiceClient;
import com.benchreadiness.ai.config.StaleAssessmentSweeper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Fix 6.1: runs this instance tracks that stay PROCESSING too long are marked FAILED. */
class AsyncAssessmentServiceStaleRunTest {

    private static final Duration MAX_AGE = Duration.ofMinutes(15);

    private final InterviewServiceClient interviewServiceClient = mock(InterviewServiceClient.class);
    private final AsyncAssessmentService service = new AsyncAssessmentService(
        mock(AssessmentService.class), mock(ComplianceServiceClient.class), interviewServiceClient, new ObjectMapper());

    @Test
    void staleProcessingRunIsMarkedFailed() {
        String runId = service.prepareAssessmentRun("iv-1");

        int failed = service.failStaleRuns(MAX_AGE, Instant.now().plus(Duration.ofMinutes(16)));

        assertThat(failed).isEqualTo(1);
        AsyncAssessmentService.AssessmentStatus s = service.getAssessmentStatus("iv-1");
        assertThat(s.getStatus()).isEqualTo("FAILED");
        assertThat(s.getError()).isEqualTo(AsyncAssessmentService.REASON_STALE_TIMEOUT);
        assertThat(s.getRunId()).isEqualTo(runId);
        verify(interviewServiceClient).updateAssessmentStatus(eq("iv-1"), argThat(m ->
            "FAILED".equals(m.get("status")) && "STALE_TIMEOUT".equals(m.get("error")) && runId.equals(m.get("runId"))));
    }

    @Test
    void freshProcessingRunIsLeftAlone() {
        service.prepareAssessmentRun("iv-1");

        assertThat(service.failStaleRuns(MAX_AGE, Instant.now().plus(Duration.ofMinutes(5)))).isZero();
        assertThat(service.getAssessmentStatus("iv-1").getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void markRunFailedDoesNotOverwriteNewerRun() {
        String oldRun = service.prepareAssessmentRun("iv-1");
        String newRun = service.prepareAssessmentRun("iv-1");

        assertThat(service.markRunFailed("iv-1", oldRun, AsyncAssessmentService.REASON_QUEUE_FULL)).isFalse();
        AsyncAssessmentService.AssessmentStatus s = service.getAssessmentStatus("iv-1");
        assertThat(s.getStatus()).isEqualTo("PROCESSING");
        assertThat(s.getRunId()).isEqualTo(newRun);
        verify(interviewServiceClient, never()).updateAssessmentStatus(eq("iv-1"), argThat(m -> "FAILED".equals(m.get("status"))));
    }

    @Test
    void sweeperUsesConfiguredThreshold() {
        AsyncAssessmentService async = mock(AsyncAssessmentService.class);
        new StaleAssessmentSweeper(async, 15).sweepStaleRuns();
        verify(async).failStaleRuns(eq(Duration.ofMinutes(15)), any(Instant.class));
    }
}
