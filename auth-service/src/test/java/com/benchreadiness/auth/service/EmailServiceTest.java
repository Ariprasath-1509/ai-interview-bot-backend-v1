package com.benchreadiness.auth.service;

import com.benchreadiness.auth.mail.MailGateway;
import com.benchreadiness.auth.mail.MailMessage;
import com.benchreadiness.auth.mail.MailResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private MailGateway mailGateway;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailGateway);
        ReflectionTestUtils.setField(service, "from", "noreply@example.test");
        ReflectionTestUtils.setField(service, "loginUrl", "https://login.example.test");
    }

    @Test
    void droppedRegistrationWelcomeNeverSends() {
        service.sendCandidateRegistrationWelcomeEmail("user@example.test", "User");
        verifyNoInteractions(mailGateway);
    }

    @Test
    void droppedInterviewScheduledNeverSends() {
        service.sendInterviewScheduledEmail("user@example.test", "User", "start", "end");
        verifyNoInteractions(mailGateway);
    }

    @Test
    void credentialsMailGoesThroughTheGatewayAsABulkMail() {
        when(mailGateway.send(any())).thenReturn(MailResult.sent("id-1"));

        service.sendCandidateWelcomeEmail("user@example.test", "User", "user@example.test", "pw-for-test");

        ArgumentCaptor<MailMessage> sent = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(sent.capture());
        assertEquals("CANDIDATE_CREDENTIALS", sent.getValue().event());
        assertEquals("user@example.test", sent.getValue().to());
        assertFalse(sent.getValue().critical(), "bulk credentials must go through the throttle");
    }

    @Test
    void credentialsFailureIsThrownSoTheCallerCanLogIt() {
        when(mailGateway.send(any())).thenReturn(MailResult.failed("http=422 postmarkErrorCode=300"));

        assertThrows(IllegalStateException.class,
                () -> service.sendCandidateWelcomeEmail("user@example.test", "User", "user@example.test", "pw"));
    }

    @Test
    void duplicateCredentialsMailIsNotAnError() {
        when(mailGateway.send(any())).thenReturn(MailResult.duplicate());

        service.sendCandidateWelcomeEmail("user@example.test", "User", "user@example.test", "pw");
    }

    @Test
    void blankRecipientSendsNothing() {
        service.sendCandidateWelcomeEmail(" ", "User", "x", "pw");
        verifyNoInteractions(mailGateway);
    }

    @Test
    void otpMailIsCriticalSoItSkipsThrottleAndDuplicateWindow() {
        when(mailGateway.send(any())).thenReturn(MailResult.sent("id-2"));

        service.sendOtpEmail("user@example.test", "123456", "User");

        ArgumentCaptor<MailMessage> sent = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(sent.capture());
        assertEquals("PASSWORD_RESET_OTP", sent.getValue().event());
        assertTrue(sent.getValue().critical());
    }

    @Test
    void otpSendFailureReachesTheCaller() {
        when(mailGateway.send(any())).thenReturn(MailResult.failed("http=500 postmarkErrorCode=-1"));

        assertThrows(IllegalStateException.class, () -> service.sendOtpEmail("user@example.test", "123456", "User"));
    }
}
