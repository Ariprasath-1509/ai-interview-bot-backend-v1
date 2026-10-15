package com.benchreadiness.ops.observer.service;

import com.benchreadiness.ops.mail.MailGateway;
import com.benchreadiness.ops.mail.MailMessage;
import com.benchreadiness.ops.mail.MailResult;
import com.benchreadiness.ops.observer.client.AuthServiceClient;
import com.benchreadiness.ops.observer.client.InterviewServiceClient;
import com.benchreadiness.ops.observer.controller.ObserverController;
import com.benchreadiness.ops.observer.dto.ClientCreatedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Report item 57: dropped events stay silent, the invite is gated, the digest goes to the caller only. */
@ExtendWith(MockitoExtension.class)
class MailReductionTest {

    @Mock private MailGateway mailGateway;
    @Mock private AuthServiceClient authServiceClient;
    @Mock private InterviewServiceClient interviewServiceClient;

    private EmailService emailService;
    private DigestService digestService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailGateway, authServiceClient);
        ReflectionTestUtils.setField(emailService, "from", "noreply@example.test");
        ReflectionTestUtils.setField(emailService, "interviewBaseUrl", "https://app.example.test/interview");

        digestService = new DigestService(mailGateway, authServiceClient, interviewServiceClient);
        ReflectionTestUtils.setField(digestService, "from", "noreply@example.test");
        ReflectionTestUtils.setField(digestService, "interviewBaseUrl", "https://app.example.test/interview");
    }

    // ---- dropped events ----

    @Test
    void droppedAbandonedMailNeverSends() {
        emailService.sendInterviewAbandoned("creator-id", "interview-1", "not_prepared");
        verifyNoInteractions(mailGateway, authServiceClient);
    }

    @Test
    void droppedClientCreatedMailNeverSends() {
        emailService.sendClientCreatedNotification(new ClientCreatedRequest());
        verifyNoInteractions(mailGateway, authServiceClient);
    }

    @Test
    void droppedCompletionNoticeNeverSendsEvenWhenTheFlagIsOn() {
        ReflectionTestUtils.setField(emailService, "completionNotifyEnabled", true);
        emailService.sendInterviewCompletedNotification(Map.of("interviewId", "interview-1", "branch", "DEVELOPMENT"));
        verifyNoInteractions(mailGateway, authServiceClient);
    }

    @Test
    void droppedCancellationMailNeverSends() {
        emailService.sendInterviewCancellation("user@example.test", "User", "interview-1", "Role", "reason");
        verifyNoInteractions(mailGateway, authServiceClient);
    }

    @Test
    void droppedEndpointsAnswerSkippedWithoutSending() {
        ObserverController controller = new ObserverController(null, null, emailService, digestService, null);
        assertEquals(Map.of("ok", true, "skipped", true),
                controller.notifyInterviewCompleted(Map.of("interviewId", "interview-1")).getBody());
        assertEquals(Map.of("ok", true, "skipped", true),
                controller.notifyInterviewCancelled(Map.of("interviewId", "interview-1")).getBody());
        verifyNoInteractions(mailGateway, authServiceClient);
    }

    // ---- kept: the interview invite goes through the gateway ----

    @Test
    void inviteGoesThroughTheGatewayKeyedByInterview() {
        when(mailGateway.send(any())).thenReturn(MailResult.sent("id-1"));

        emailService.sendInterviewInvite("user@example.test", "User", "interview-1");

        ArgumentCaptor<MailMessage> sent = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(sent.capture());
        assertEquals("INTERVIEW_INVITE", sent.getValue().event());
        assertEquals("user@example.test", sent.getValue().to());
        assertEquals("interview-1", sent.getValue().dedupKey());
        assertFalse(sent.getValue().critical());
    }

    @Test
    void inviteFailureIsThrownSoTheCallerCanAnswerWithAnError() {
        when(mailGateway.send(any())).thenReturn(MailResult.failed("http=422 postmarkErrorCode=406"));

        assertThrows(IllegalStateException.class,
                () -> emailService.sendInterviewInvite("user@example.test", "User", "interview-1"));
    }

    @Test
    void duplicateInviteIsNotAnError() {
        when(mailGateway.send(any())).thenReturn(MailResult.duplicate());

        emailService.sendInterviewInvite("user@example.test", "User", "interview-1");
    }

    // ---- digest: manual, caller only, caller-scoped data ----

    @Test
    void digestForUnsupportedRoleSendsNothing() {
        assertEquals(DigestService.DigestResult.UNSUPPORTED_ROLE, digestService.sendDigestToCaller("u1", "RECRUITER"));
        verifyNoInteractions(mailGateway, authServiceClient, interviewServiceClient);
    }

    @Test
    void digestWithoutEmailOnFileSendsNothing() {
        when(authServiceClient.getUser("u1")).thenReturn(Map.of("name", "Admin"));
        assertEquals(DigestService.DigestResult.NO_EMAIL, digestService.sendDigestToCaller("u1", "ADMIN"));
        verify(mailGateway, never()).send(any());
    }

    @Test
    void digestUsesOnlyTheCallerScopedDataAndMailsOnlyTheCaller() {
        when(mailGateway.send(any())).thenReturn(MailResult.sent("id-2"));
        when(authServiceClient.getUser("u1")).thenReturn(Map.of("email", "caller@example.test"));
        when(interviewServiceClient.getDailyReportForCaller()).thenReturn(Map.of());
        when(authServiceClient.getCandidatePipelineStatusForCaller()).thenReturn(Map.of());

        assertEquals(DigestService.DigestResult.SENT, digestService.sendDigestToCaller("u1", "ROLE_ADMIN"));

        ArgumentCaptor<MailMessage> sent = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(sent.capture());
        assertEquals("DAILY_DIGEST", sent.getValue().event());
        assertEquals("caller@example.test", sent.getValue().to());
        verify(authServiceClient, never()).getAdmins();
    }

    @Test
    void digestClickedTwiceIsAnsweredAsADuplicate() {
        when(mailGateway.send(any())).thenReturn(MailResult.duplicate());
        when(authServiceClient.getUser("u1")).thenReturn(Map.of("email", "caller@example.test"));
        when(interviewServiceClient.getDailyReportForCaller()).thenReturn(Map.of());
        when(authServiceClient.getCandidatePipelineStatusForCaller()).thenReturn(Map.of());

        assertEquals(DigestService.DigestResult.DUPLICATE, digestService.sendDigestToCaller("u1", "ADMIN"));
    }

    @Test
    void digestProviderFailureIsReportedNotThrown() {
        when(mailGateway.send(any())).thenReturn(MailResult.failed("http=500 postmarkErrorCode=-1"));
        when(authServiceClient.getUser("u1")).thenReturn(Map.of("email", "caller@example.test"));
        when(interviewServiceClient.getDailyReportForCaller()).thenReturn(Map.of());
        when(authServiceClient.getCandidatePipelineStatusForCaller()).thenReturn(Map.of());

        assertEquals(DigestService.DigestResult.FAILED, digestService.sendDigestToCaller("u1", "ADMIN"));
    }

    @Test
    void digestLookupFailureIsReportedNotThrown() {
        when(authServiceClient.getUser("u1")).thenThrow(new RuntimeException("auth down"));
        assertEquals(DigestService.DigestResult.FAILED, digestService.sendDigestToCaller("u1", "SUPER_ADMIN"));
    }
}
