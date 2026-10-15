package com.benchreadiness.ops.observer.service;

import com.benchreadiness.ops.observer.client.AuthServiceClient;
import com.benchreadiness.ops.observer.client.InterviewServiceClient;
import com.benchreadiness.ops.observer.dto.ClientCreatedRequest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Report item 57: dropped events stay silent, the digest goes to the caller only. */
@ExtendWith(MockitoExtension.class)
class MailReductionTest {

    @Mock private JavaMailSender mailSender;
    @Mock private AuthServiceClient authServiceClient;
    @Mock private InterviewServiceClient interviewServiceClient;

    private EmailService emailService;
    private DigestService digestService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, authServiceClient);
        ReflectionTestUtils.setField(emailService, "from", "noreply@example.test");
        ReflectionTestUtils.setField(emailService, "interviewBaseUrl", "https://app.example.test/interview");

        digestService = new DigestService(mailSender, authServiceClient, interviewServiceClient);
        ReflectionTestUtils.setField(digestService, "from", "noreply@example.test");
        ReflectionTestUtils.setField(digestService, "interviewBaseUrl", "https://app.example.test/interview");
    }

    @Test
    void droppedAbandonedMailNeverSends() {
        emailService.sendInterviewAbandoned("creator-id", "interview-1", "not_prepared");
        verifyNoInteractions(mailSender, authServiceClient);
    }

    @Test
    void droppedClientCreatedMailNeverSends() {
        emailService.sendClientCreatedNotification(new ClientCreatedRequest());
        verifyNoInteractions(mailSender, authServiceClient);
    }

    @Test
    void digestForUnsupportedRoleSendsNothing() {
        assertEquals(DigestService.DigestResult.UNSUPPORTED_ROLE, digestService.sendDigestToCaller("u1", "RECRUITER"));
        verifyNoInteractions(mailSender, authServiceClient, interviewServiceClient);
    }

    @Test
    void digestWithoutEmailOnFileSendsNothing() {
        when(authServiceClient.getUser("u1")).thenReturn(Map.of("name", "Admin"));
        assertEquals(DigestService.DigestResult.NO_EMAIL, digestService.sendDigestToCaller("u1", "ADMIN"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void digestGoesToTheCallerOnlyAndNeverLooksUpOtherAdmins() {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);
        when(authServiceClient.getUser("u1")).thenReturn(Map.of("email", "caller@example.test"));
        when(interviewServiceClient.getDailyReportDataForBranch("DEVELOPMENT")).thenReturn(Map.of());

        assertEquals(DigestService.DigestResult.SENT, digestService.sendDigestToCaller("u1", "ROLE_ADMIN"));

        verify(mailSender).send(mime);
        verify(authServiceClient, never()).getAdmins();
    }

    @Test
    void digestFailureIsReportedNotThrown() {
        when(authServiceClient.getUser("u1")).thenThrow(new RuntimeException("auth down"));
        assertEquals(DigestService.DigestResult.FAILED, digestService.sendDigestToCaller("u1", "SUPER_ADMIN"));
    }
}
