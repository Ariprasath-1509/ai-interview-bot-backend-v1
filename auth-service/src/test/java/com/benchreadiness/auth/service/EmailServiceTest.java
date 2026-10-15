package com.benchreadiness.auth.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender);
        ReflectionTestUtils.setField(service, "from", "noreply@example.test");
        ReflectionTestUtils.setField(service, "loginUrl", "https://login.example.test");
    }

    @Test
    void droppedRegistrationWelcomeNeverSends() {
        service.sendCandidateRegistrationWelcomeEmail("user@example.test", "User");
        verifyNoInteractions(mailSender);
    }

    @Test
    void droppedInterviewScheduledNeverSends() {
        service.sendInterviewScheduledEmail("user@example.test", "User", "start", "end");
        verifyNoInteractions(mailSender);
    }

    @Test
    void otpMailIsSentOnce() {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);

        service.sendOtpEmail("user@example.test", "123456", "User");

        verify(mailSender).send(mime);
    }

    @Test
    void otpSendFailureReachesTheCaller() {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("provider down")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () -> service.sendOtpEmail("user@example.test", "123456", "User"));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
