package com.benchreadiness.screening.mail;

import com.benchreadiness.screening.entity.ScreeningBatch;
import com.benchreadiness.screening.entity.ScreeningCandidate;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Same JavaMailSender + branded-HTML-template approach as ops-service's EmailService — copied
 * rather than shared, to keep screening-service's isolation from the rest of the platform intact.
 */
@Service
public class ScreeningMailService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningMailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.screening.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.screening.candidate-link-path}")
    private String candidateLinkPath;

    public ScreeningMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Single source of truth for the candidate's Round 1 link — also used by the admin UI to show a test link. */
    public String buildCandidateLink(ScreeningCandidate candidate) {
        return frontendBaseUrl + candidateLinkPath + "/" + candidate.getToken();
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (MessagingException e) {
            log.error("Failed to send screening email to {}: {}", to, e.getMessage());
        }
    }

    public void sendCandidateInvite(ScreeningCandidate candidate, ScreeningBatch batch) {
        String link = buildCandidateLink(candidate);
        String deadline = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'")
                .withZone(ZoneOffset.UTC).format(batch.getDeadline());

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Test", batch.getLanguage());
        details.put("Deadline", deadline);
        details.put("Attempts", "Single-use — complete in one sitting");

        String content = buildDetailsCard("Test Details", details, "#7c3aed") +
                "<div style='background-color:#faf5ff;border:2px solid #7c3aed;padding:20px;margin:25px 0;border-radius:8px;text-align:center;'>" +
                "<p style='margin:0 0 15px;color:#5b21b6;font-size:14px;font-weight:600;'>TEST ACCESS LINK</p>" +
                "<a href='" + link + "' style='display:inline-block;background:linear-gradient(135deg,#7c3aed 0%,#6d28d9 100%);color:#ffffff;text-decoration:none;padding:14px 32px;border-radius:8px;font-weight:600;font-size:15px;'>Start Your Test</a>" +
                "<p style='margin:15px 0 0;color:#6b7280;font-size:12px;'>Or copy: <span style='color:#7c3aed;'>" + link + "</span></p></div>";

        String html = buildEmailTemplate("Screening Test Invitation", "#7c3aed", candidate.getName(),
                "You've been invited to take a short <strong>" + escapeHtml(batch.getLanguage()) + "</strong> screening test as the first step of our hiring process.",
                content,
                "This link is single-use and must be completed before the deadline above. Good luck!",
                "Start Your Test", link);

        sendHtml(candidate.getEmail(), "Your " + batch.getLanguage() + " Screening Test - Bench Readiness", html);
    }

    public void sendConsolidatedReport(ScreeningBatch batch, List<ScreeningCandidate> candidates) {
        StringBuilder rows = new StringBuilder();
        rows.append("<tr><th style='text-align:left;padding:8px;border-bottom:2px solid #e2e8f0;'>Name</th>")
                .append("<th style='text-align:left;padding:8px;border-bottom:2px solid #e2e8f0;'>Email</th>")
                .append("<th style='text-align:left;padding:8px;border-bottom:2px solid #e2e8f0;'>Status</th>")
                .append("<th style='text-align:left;padding:8px;border-bottom:2px solid #e2e8f0;'>Score</th></tr>");
        for (ScreeningCandidate c : candidates) {
            String status = switch (c.getStage()) {
                case ROUND1_SUBMITTED, ROUND1_PASSED, ROUND1_FAILED -> "Submitted";
                default -> "Did not complete";
            };
            String score = c.getRound1Score() != null ? (c.getRound1Score() + " / 35") : "-";
            rows.append("<tr><td style='padding:8px;border-bottom:1px solid #edf2f7;'>").append(escapeHtml(c.getName())).append("</td>")
                    .append("<td style='padding:8px;border-bottom:1px solid #edf2f7;'>").append(escapeHtml(c.getEmail())).append("</td>")
                    .append("<td style='padding:8px;border-bottom:1px solid #edf2f7;'>").append(status).append("</td>")
                    .append("<td style='padding:8px;border-bottom:1px solid #edf2f7;'>").append(score).append("</td></tr>");
        }
        String content = "<table width='100%' cellpadding='0' cellspacing='0' style='margin:20px 0;font-size:14px;'>" + rows + "</table>";

        String html = buildEmailTemplate(batch.getLanguage() + " Screening Results", "#7c3aed", batch.getAssignerName(),
                "The <strong>" + escapeHtml(batch.getLanguage()) + "</strong> screening batch you created has closed. Here's a summary:",
                content,
                "Full per-question feedback is available in the admin screening dashboard.",
                "View Screening Dashboard", frontendBaseUrl + "/admin/screening");

        sendHtml(batch.getAssignerEmail(), batch.getLanguage() + " Screening Results - Bench Readiness", html);
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String buildEmailTemplate(String title, String accentColor, String recipientName,
                                      String introText, String contentHtml, String footerText,
                                      String buttonText, String buttonLink) {
        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><title>" + title + "</title></head>" +
            "<body style='margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,Arial,sans-serif;background:#f5f5f5;'>" +
            "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f5f5f5;padding:40px 20px;'><tr><td align='center'>" +
            "<table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;box-shadow:0 4px 6px rgba(0,0,0,0.1);overflow:hidden;'>" +
            "<tr><td style='background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);padding:40px 30px;text-align:center;'>" +
            "<div style='background:rgba(255,255,255,0.95);display:inline-block;padding:15px 30px;border-radius:50px;margin-bottom:20px;'>" +
            "<h1 style='margin:0;color:#667eea;font-size:28px;font-weight:700;'><span style='color:#764ba2;'>●</span> Bench Readiness</h1></div>" +
            "<h2 style='margin:0;color:#ffffff;font-size:24px;font-weight:600;'>" + title + "</h2></td></tr>" +
            "<tr><td style='padding:40px 30px;'>" +
            "<p style='margin:0 0 20px;color:#333;font-size:16px;line-height:1.6;'>Hi <strong>" + escapeHtml(recipientName) + "</strong>,</p>" +
            "<p style='margin:0 0 25px;color:#555;font-size:15px;line-height:1.6;'>" + introText + "</p>" +
            contentHtml +
            "<p style='margin:25px 0 20px;color:#555;font-size:15px;line-height:1.6;'>" + footerText + "</p>" +
            "<div style='text-align:center;margin:30px 0;'>" +
            "<a href='" + buttonLink + "' style='display:inline-block;background:linear-gradient(135deg," + accentColor + " 0%," + accentColor + " 100%);color:#ffffff;text-decoration:none;padding:14px 32px;border-radius:8px;font-weight:600;font-size:15px;'>" + buttonText + "</a></div>" +
            "</td></tr>" +
            "<tr><td style='background:#f7fafc;padding:30px;text-align:center;border-top:1px solid #e2e8f0;'>" +
            "<p style='margin:0 0 10px;color:#2d3748;font-size:16px;font-weight:600;'>Best regards,</p>" +
            "<p style='margin:0 0 15px;color:#667eea;font-size:18px;font-weight:700;'>Bench Readiness Team</p>" +
            "<p style='margin:0;color:#a0aec0;font-size:12px;'>Need help? <a href='mailto:" + from + "' style='color:#667eea;'>" + from + "</a></p>" +
            "</td></tr></table></td></tr></table></body></html>";
    }

    private String buildDetailsCard(String cardTitle, Map<String, String> details, String borderColor) {
        StringBuilder rows = new StringBuilder();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            rows.append("<tr><td style='color:#718096;font-size:14px;padding:8px 0;border-bottom:1px solid #e2e8f0;'>")
                .append(entry.getKey()).append(":</td>")
                .append("<td style='color:#2d3748;font-size:14px;font-weight:600;padding:8px 0;text-align:right;border-bottom:1px solid #e2e8f0;'>")
                .append(entry.getValue()).append("</td></tr>");
        }
        return "<div style='background:#f7fafc;border-left:4px solid " + borderColor + ";padding:20px;margin:25px 0;border-radius:8px;'>" +
            "<p style='margin:0 0 15px;color:#2d3748;font-size:14px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;'>" + cardTitle + "</p>" +
            "<table width='100%' cellpadding='0' cellspacing='0'>" + rows + "</table></div>";
    }
}
