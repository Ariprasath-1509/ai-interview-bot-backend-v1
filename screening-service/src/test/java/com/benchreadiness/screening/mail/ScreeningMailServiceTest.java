package com.benchreadiness.screening.mail;

import com.benchreadiness.screening.entity.ScreeningBatch;
import com.benchreadiness.screening.entity.ScreeningCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The two kept screening mails go through the gateway and keep their old behaviour: a failure is not thrown. */
@ExtendWith(MockitoExtension.class)
class ScreeningMailServiceTest {

    @Mock
    private MailGateway mailGateway;

    private ScreeningMailService service;
    private ScreeningBatch batch;
    private ScreeningCandidate candidate;

    @BeforeEach
    void setUp() {
        service = new ScreeningMailService(mailGateway);
        ReflectionTestUtils.setField(service, "from", "noreply@example.test");
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://app.example.test");
        ReflectionTestUtils.setField(service, "candidateLinkPath", "/screening");

        batch = new ScreeningBatch();
        batch.setId("batch-1");
        batch.setLanguage("Java");
        batch.setAssignerName("Assigner");
        batch.setAssignerEmail("assigner@example.test");
        batch.setDeadline(Instant.parse("2026-10-30T10:00:00Z"));

        candidate = new ScreeningCandidate();
        candidate.setName("Candidate");
        candidate.setEmail("candidate@example.test");
        candidate.setToken("token-1");
    }

    @Test
    void inviteIsKeyedByBatchAndGoesToTheCandidate() {
        when(mailGateway.send(any())).thenReturn(MailResult.sent("id-1"));

        service.sendCandidateInvite(candidate, batch);

        ArgumentCaptor<MailMessage> sent = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(sent.capture());
        assertEquals("SCREENING_INVITE", sent.getValue().event());
        assertEquals("candidate@example.test", sent.getValue().to());
        assertEquals("batch-1", sent.getValue().dedupKey());
        assertFalse(sent.getValue().critical());
    }

    @Test
    void reportGoesOnceToTheAssigner() {
        when(mailGateway.send(any())).thenReturn(MailResult.sent("id-2"));

        service.sendConsolidatedReport(batch, List.of());

        ArgumentCaptor<MailMessage> sent = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(sent.capture());
        assertEquals("SCREENING_REPORT", sent.getValue().event());
        assertEquals("assigner@example.test", sent.getValue().to());
        assertEquals("batch-1", sent.getValue().dedupKey());
    }

    @Test
    void failedInviteIsLoggedByTheGatewayAndNotThrown() {
        when(mailGateway.send(any())).thenReturn(MailResult.failed("http=422 postmarkErrorCode=406"));

        service.sendCandidateInvite(candidate, batch);
    }
}
