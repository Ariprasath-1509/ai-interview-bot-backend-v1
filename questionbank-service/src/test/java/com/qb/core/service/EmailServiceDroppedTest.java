package com.qb.core.service;

import com.qb.core.repository.EmailLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Report item 57: the dropped questionbank mails never send. */
class EmailServiceDroppedTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final PdfService pdfService = mock(PdfService.class);
    private final EmailLogRepository logRepo = mock(EmailLogRepository.class);
    private final EmailService service = new EmailService(mailSender, pdfService, logRepo);

    @Test
    void interviewPrepMailsAreNotSent() {
        var result = service.sendInterviewPrepEmails(UUID.randomUUID(),
                List.of(Map.of("email", "user@example.test")), List.of(), Map.of());
        assertEquals(0, result.emailsSent());
        verifyNoInteractions(mailSender, pdfService, logRepo);
    }

    @Test
    void verificationAndResetMailsAreNotSent() {
        service.sendVerificationEmail("user@example.test", "User", "token");
        service.sendPasswordResetEmail("user@example.test", "User", "token");
        verifyNoInteractions(mailSender);
    }
}
