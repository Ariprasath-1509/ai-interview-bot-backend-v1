package com.benchreadiness.ops.observer.service;

import com.benchreadiness.ops.observer.client.AuthServiceClient;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final AuthServiceClient authServiceClient;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.interview-base-url}")
    private String interviewBaseUrl;

    public EmailService(JavaMailSender mailSender, AuthServiceClient authServiceClient) {
        this.mailSender = mailSender;
        this.authServiceClient = authServiceClient;
    }

    public void sendInterviewInvite(String toEmail, String candidateName, String interviewId) {
        String name = (candidateName != null && !candidateName.isBlank()) ? candidateName : "Candidate";
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Your Technical Interview is Scheduled - Bench Readiness");
            helper.setText(buildInterviewInviteTemplate(name, interviewId), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("Your Technical Interview is Scheduled - Bench Readiness");
            message.setText("Hi " + name + ",\n\nYour technical interview has been scheduled.\n\n"
                + interviewBaseUrl + "/" + interviewId + "\n\nGood luck!\nBench Readiness Team");
            mailSender.send(message);
        }
    }

    public void sendInterviewAbandoned(String createdByUserId, String interviewId, String reason) {
        String managerEmail;
        String managerName = "Admin";
        try {
            Map<String, Object> user = authServiceClient.getUser(createdByUserId);
            managerEmail = (String) user.get("email");
            managerName = (String) user.getOrDefault("name", "Admin");
        } catch (Exception e) {
            return;
        }
        if (managerEmail == null || managerEmail.isBlank()) return;

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(managerEmail);
            helper.setSubject(abandonedSubject(reason));
            helper.setText(buildInterviewAbandonedTemplate(managerName, interviewId, reason), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(managerEmail);
            message.setSubject(abandonedSubject(reason));
            message.setText("Interview ID: " + interviewId + "\nReason: " + reason
                + "\nReview: " + interviewBaseUrl.replace("/interview", "") + "/admin/interviews/" + interviewId + "/review");
            mailSender.send(message);
        }
    }

    private String abandonedSubject(String reason) {
        return switch (reason) {
            case "tab_switch_violation" -> "Interview Terminated - Tab Switch Violation";
            case "not_prepared" -> "Interview Ended Early - Candidate Not Prepared";
            case "ai_manipulation" -> "Interview Terminated - AI Manipulation Detected";
            case "proctoring_violation" -> "Interview Terminated - Video Proctoring Violation";
            default -> "Interview Time Limit Reached";
        };
    }

    public void sendClientCreatedNotification(String clientId, String clientName, String jdRole,
                                              Integer benchB2bCandidatesNeeded, Integer marketCandidatesNeeded) {
        try {
            List<Map<String, Object>> staff = authServiceClient.getAllStaff();
            if (benchB2bCandidatesNeeded > 0) {
                staff.stream()
                    .filter(u -> "ADMIN".equals(u.get("role")) && "BENCH".equals(u.get("adminSource")))
                    .findFirst()
                    .ifPresent(admin -> sendClientNotificationEmail(
                        (String) admin.get("email"), (String) admin.get("name"),
                        clientId, clientName, jdRole, benchB2bCandidatesNeeded, "BENCH/B2B"));
            }
            if (marketCandidatesNeeded > 0) {
                staff.stream()
                    .filter(u -> "ADMIN".equals(u.get("role")) && "RECRUITMENT".equals(u.get("adminSource")))
                    .findFirst()
                    .ifPresent(admin -> sendClientNotificationEmail(
                        (String) admin.get("email"), (String) admin.get("name"),
                        clientId, clientName, jdRole, marketCandidatesNeeded, "MARKET"));
            }
        } catch (Exception e) {
            System.err.println("Failed to send client creation notifications: " + e.getMessage());
        }
    }

    private void sendClientNotificationEmail(String toEmail, String adminName, String clientId,
                                             String clientName, String jdRole, Integer candidatesNeeded, String source) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("New Client Position - Action Required");
            helper.setText(buildClientCreatedTemplate(adminName, clientId, clientName, jdRole, candidatesNeeded, source), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("New Client Position - Action Required");
            message.setText("Hi " + (adminName != null ? adminName : "Admin") + ",\n\nNew client: " + clientName
                + "\nRole: " + jdRole + "\nCandidates needed (" + source + "): " + candidatesNeeded
                + "\n\n" + interviewBaseUrl.replace("/interview", "") + "/admin/clients");
            mailSender.send(message);
        }
    }

    public void sendInterviewCancellation(String toEmail, String candidateName, String interviewId, String jdTitle, String reason) {
        String name = (candidateName != null && !candidateName.isBlank()) ? candidateName : "Candidate";
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Interview Cancelled - Bench Readiness");
            helper.setText(buildCancellationEmailTemplate(name, interviewId, jdTitle, reason), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("Interview Cancelled - Bench Readiness");
            message.setText("Hi " + name + ",\n\nYour interview has been cancelled.\nInterview ID: " + interviewId
                + (jdTitle != null ? "\nPosition: " + jdTitle : "")
                + (reason != null ? "\nReason: " + reason : "")
                + "\n\nBench Readiness Team");
            mailSender.send(message);
        }
    }

    private String buildCancellationEmailTemplate(String candidateName, String interviewId, String jdTitle, String reason) {
        return buildEmailTemplate("Interview Cancelled", "#e53e3e", candidateName,
            "We regret to inform you that your scheduled technical interview has been <strong style='color: #e53e3e;'>cancelled</strong>.",
            buildDetailsCard("Interview Details", Map.of(
                "Interview ID", interviewId,
                "Position", jdTitle != null ? jdTitle : "N/A",
                "Reason", reason != null ? reason : "Administrative decision"
            ), "#e53e3e"),
            "If you have any questions or would like to reschedule, please contact our recruitment team.",
            "Contact Support", "mailto:" + from);
    }

    private String buildInterviewInviteTemplate(String candidateName, String interviewId) {
        String interviewLink = interviewBaseUrl + "/" + interviewId;
        String currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
        return buildEmailTemplate("Interview Scheduled", "#10b981", candidateName,
            "Your technical interview has been successfully scheduled. We're excited to learn more about your skills and experience!",
            buildDetailsCard("Interview Details", Map.of(
                "Interview ID", interviewId, "Scheduled Date", currentDate, "Status", "Ready to Start"
            ), "#10b981") +
            "<div style='background-color: #f0fdf4; border: 2px solid #10b981; padding: 20px; margin: 25px 0; border-radius: 8px; text-align: center;'>" +
            "<p style='margin: 0 0 15px; color: #065f46; font-size: 14px; font-weight: 600;'>INTERVIEW ACCESS LINK</p>" +
            "<a href='" + interviewLink + "' style='display: inline-block; background: linear-gradient(135deg, #10b981 0%, #059669 100%); color: #ffffff; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: 600; font-size: 15px;'>Start Interview</a>" +
            "<p style='margin: 15px 0 0; color: #6b7280; font-size: 12px;'>Or copy: <span style='color: #10b981;'>" + interviewLink + "</span></p></div>",
            "<strong>Important:</strong> Log in with your registered credentials to begin. Good luck!",
            "View Dashboard", interviewBaseUrl.replace("/interview", "") + "/candidate/dashboard");
    }

    private String buildInterviewAbandonedTemplate(String managerName, String interviewId, String reason) {
        String reviewLink = interviewBaseUrl.replace("/interview", "") + "/admin/interviews/" + interviewId + "/review";
        String reasonText = switch (reason) {
            case "tab_switch_violation" -> "Candidate switched tabs/windows 2+ times (automatic termination)";
            case "not_prepared" -> "Candidate indicated they are not prepared";
            case "ai_manipulation" -> "AI manipulation detected (automatic termination)";
            case "proctoring_violation" -> "Video proctoring violation detected (automatic termination)";
            default -> "Interview time limit reached";
        };
        String statusColor = "not_prepared".equals(reason) ? "#f59e0b" : "#dc2626";
        return buildEmailTemplate("Interview Ended Early", statusColor, managerName,
            "An interview has ended before completion. Please review the transcript and provide feedback.",
            buildDetailsCard("Interview Details", Map.of(
                "Interview ID", interviewId, "Status", "Ended Early / WITHDRAWN",
                "Reason", reasonText, "Action Required", "Review & Sign-off"
            ), statusColor),
            "Please review the interview transcript and candidate responses to provide appropriate feedback and sign-off.",
            "Review Interview", reviewLink);
    }

    private String buildClientCreatedTemplate(String adminName, String clientId, String clientName,
                                              String jdRole, Integer candidatesNeeded, String source) {
        return buildEmailTemplate("New Client Position", "#3b82f6", adminName,
            "A new client position has been created and requires your attention for candidate matching.",
            buildDetailsCard("Position Details", Map.of(
                "Client", clientName, "Role", jdRole,
                "Candidates Needed", candidatesNeeded.toString(), "Source", source, "Status", "Awaiting Candidate Match"
            ), "#3b82f6"),
            "Please review the position requirements and trigger AI-powered candidate matching.",
            "View Client & Match Candidates", interviewBaseUrl.replace("/interview", "") + "/admin/clients");
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
            "<p style='margin:0 0 20px;color:#333;font-size:16px;line-height:1.6;'>Hi <strong>" + recipientName + "</strong>,</p>" +
            "<p style='margin:0 0 25px;color:#555;font-size:15px;line-height:1.6;'>" + introText + "</p>" +
            contentHtml +
            "<p style='margin:25px 0 20px;color:#555;font-size:15px;line-height:1.6;'>" + footerText + "</p>" +
            "<div style='text-align:center;margin:30px 0;'>" +
            "<a href='" + buttonLink + "' style='display:inline-block;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:#ffffff;text-decoration:none;padding:14px 32px;border-radius:8px;font-weight:600;font-size:15px;'>" + buttonText + "</a></div>" +
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
