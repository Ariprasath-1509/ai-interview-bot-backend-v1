package com.qb.core.service;

import com.qb.core.dto.QuestionDTO;
import com.qb.core.entity.EmailLog;
import com.qb.core.repository.EmailLogRepository;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends transactional emails via Gmail SMTP with PDF attachment.
 * Logs every send to email_logs table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender     mailSender;
    private final PdfService         pdfService;
    private final EmailLogRepository emailLogRepo;

    @Value("${app.email.sender-email}")
    private String senderEmail;

    @Value("${app.email.sender-name:QuestionBank}")
    private String senderName;

    public record SendResult(int emailsSent, List<String> errors) {}

    public SendResult sendInterviewPrepEmails(
            UUID sentByUserId,
            List<Map<String, String>> recipients,   // [{email, name, company, round, date}]
            List<QuestionDTO> questions,
            Map<String, String> filters
    ) {
        int sent = 0;
        List<String> errors = new java.util.ArrayList<>();
        List<String> recipientEmails = new java.util.ArrayList<>();

        for (Map<String, String> recipient : recipients) {
            String email   = recipient.get("email");
            String name    = recipient.getOrDefault("name", "Candidate");
            String company = recipient.getOrDefault("company", "");
            String round   = recipient.getOrDefault("round", "");
            String date    = recipient.getOrDefault("date", "");

            try {
                // Generate PDF for this recipient
                byte[] pdfBytes = pdfService.generateQuestionListPdf(name, company, round, date, questions);

                String subject = String.format("Interview Prep — %s %s", company, round.toUpperCase());

                // Build and send MIME message with attachment
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                helper.setFrom(senderEmail, senderName);
                helper.setTo(email);
                helper.setSubject(subject);
                helper.setText(buildHtmlBody(name, company, round, date, questions.size()), true);
                helper.addAttachment("interview-prep.pdf",
                        new ByteArrayDataSource(pdfBytes, "application/pdf"));

                mailSender.send(message);

                recipientEmails.add(email);
                sent++;
                log.info("Email sent to {} for {} {}", email, company, round);

            } catch (Exception e) {
                log.error("Failed to send email to {}: {}", email, e.getMessage());
                errors.add(email + ": " + e.getMessage());
            }
        }

        // Log the batch
        if (sent > 0) {
            String subject = recipients.isEmpty() ? "Interview Prep"
                    : String.format("Interview Prep — %s", recipients.get(0).getOrDefault("company", ""));
            emailLogRepo.save(EmailLog.builder()
                    .sentBy(sentByUserId)
                    .subject(subject)
                    .recipientCount(sent)
                    .recipientEmails(recipientEmails)
                    .filters(filters)
                    .sentAt(Instant.now())
                    .build());
        }

        return new SendResult(sent, errors);
    }

    private String buildHtmlBody(String name, String company, String round, String date, int questionCount) {
        return String.format("""
                <div style="font-family: monospace; background: #0a0a0a; color: #eee; padding: 32px;">
                  <h2 style="color: #00f0ff;">Interview Prep — %s</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Please find attached your interview preparation material for:</p>
                  <ul>
                    <li><strong>Company:</strong> %s</li>
                    <li><strong>Round:</strong> %s</li>
                    <li><strong>Date:</strong> %s</li>
                    <li><strong>Questions:</strong> %d</li>
                  </ul>
                  <p style="color: #888; font-size: 12px;">Sent by QuestionBank</p>
                </div>
                """, company, name, company, round.toUpperCase(), date, questionCount);
    }

    public void sendVerificationEmail(String toEmail, String name, String token) {
        try {
            String subject = "Verify your QuestionBank Account";
            String verificationUrl = "http://localhost:5173/verify?token=" + token;

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, senderName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(buildVerificationHtmlBody(name, verificationUrl), true);

            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    private String buildVerificationHtmlBody(String name, String verificationUrl) {
        return String.format("""
                <div style="font-family: monospace; background: #0a0a0a; color: #eee; padding: 40px 32px; max-width: 600px; margin: 0 auto; border: 1px solid #333; border-radius: 4px;">
                  <h2 style="color: #00f0ff; font-weight: normal; margin-top: 0; border-bottom: 1px solid #333; padding-bottom: 16px;">System.out.println("Welcome");</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>A new account was created on QuestionBank with this email address.</p>
                  <p>To initialize your session and activate your account, please click the button below:</p>
                  <div style="margin: 40px 0;">
                    <a href="%s" style="background: #00f0ff; color: #0a0a0a; padding: 12px 24px; text-decoration: none; font-weight: bold; font-size: 14px; text-transform: uppercase;">[ EXECUTE_VERIFICATION ]</a>
                  </div>
                  <p style="color: #888; font-size: 12px; margin-top: 40px; border-top: 1px dashed #333; padding-top: 16px;">
                    If you didn't request this, you can safely ignore this email. Connection will automatically close.
                  </p>
                </div>
                """, name, verificationUrl);
    }

    public void sendPasswordResetEmail(String toEmail, String name, String token) {
        try {
            String subject = "Password Reset Request";
            String resetUrl = "http://localhost:5173/reset-password?token=" + token;

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, senderName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(buildPasswordResetHtmlBody(name, resetUrl), true);

            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    private String buildPasswordResetHtmlBody(String name, String resetUrl) {
        return String.format("""
                <div style="font-family: monospace; background: #0a0a0a; color: #eee; padding: 40px 32px; max-width: 600px; margin: 0 auto; border: 1px solid #333; border-radius: 4px;">
                  <h2 style="color: #ff0055; font-weight: normal; margin-top: 0; border-bottom: 1px solid #333; padding-bottom: 16px;">WARN: Auth token regeneration requested</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>We received a request to reset the password for your QuestionBank account.</p>
                  <p>This link is valid for exactly 15 minutes. Click the button below to set a new passkey:</p>
                  <div style="margin: 40px 0;">
                    <a href="%s" style="background: #ff0055; color: #0a0a0a; padding: 12px 24px; text-decoration: none; font-weight: bold; font-size: 14px; text-transform: uppercase;">[ REGENERATE_PASSKEY ]</a>
                  </div>
                  <p style="color: #888; font-size: 12px; margin-top: 40px; border-top: 1px dashed #333; padding-top: 16px;">
                    If you didn't request a password reset, your account is still secure and you can ignore this email.
                  </p>
                </div>
                """, name, resetUrl);
    }
}
