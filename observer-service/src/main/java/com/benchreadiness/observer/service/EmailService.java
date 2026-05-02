package com.benchreadiness.observer.service;

import com.benchreadiness.observer.client.AuthServiceClient;
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
            
            String htmlContent = buildInterviewInviteTemplate(name, interviewId);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            // Fallback to plain text
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("Your Technical Interview is Scheduled - Bench Readiness");
            message.setText(
                "Hi " + name + ",\n\n" +
                "Your technical interview has been scheduled. Please use the link below to access it:\n\n" +
                interviewBaseUrl + "/" + interviewId + "\n\n" +
                "Log in with your credentials to begin when you're ready.\n\n" +
                "Good luck!\n" +
                "Bench Readiness Team"
            );
            mailSender.send(message);
        }
    }

    public void sendInterviewAbandoned(String createdByUserId, String interviewId, String reason) {
        String managerEmail = null;
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
            
            String subject = "not_prepared".equals(reason)
                ? "Interview Ended Early - Candidate Not Prepared"
                : "Interview Time Limit Reached";
            helper.setSubject(subject);
            
            String htmlContent = buildInterviewAbandonedTemplate(managerName, interviewId, reason);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            // Fallback to plain text
            String subject = "not_prepared".equals(reason)
                ? "Interview ended early — candidate not prepared"
                : "Interview ended — time limit reached";

            String body = "not_prepared".equals(reason)
                ? "A candidate has ended their interview early indicating they are not prepared.\n\n"
                : "A candidate's interview has ended as the time limit was reached.\n\n";

            body += "Interview ID: " + interviewId + "\n" +
                    "Review link: " + interviewBaseUrl.replace("/interview", "") + "/admin/interviews/" + interviewId + "/review\n\n" +
                    "Please review and sign off when ready.\n\n" +
                    "Bench Readiness Team";

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(managerEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        }
    }

    public void sendClientCreatedNotification(String clientId, String clientName, String jdRole, 
                                            Integer benchB2bCandidatesNeeded, Integer marketCandidatesNeeded) {
        try {
            List<Map<String, Object>> staff = authServiceClient.getAllStaff();
            
            if (benchB2bCandidatesNeeded > 0) {
                staff.stream()
                    .filter(user -> "ADMIN".equals(user.get("role")) && "BENCH".equals(user.get("adminSource")))
                    .findFirst()
                    .ifPresent(admin -> sendClientNotificationEmail(
                        (String) admin.get("email"),
                        (String) admin.get("name"),
                        clientId, clientName, jdRole, benchB2bCandidatesNeeded, "BENCH/B2B"
                    ));
            }
            
            if (marketCandidatesNeeded > 0) {
                staff.stream()
                    .filter(user -> "ADMIN".equals(user.get("role")) && "RECRUITMENT".equals(user.get("adminSource")))
                    .findFirst()
                    .ifPresent(admin -> sendClientNotificationEmail(
                        (String) admin.get("email"),
                        (String) admin.get("name"),
                        clientId, clientName, jdRole, marketCandidatesNeeded, "MARKET"
                    ));
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
            
            String htmlContent = buildClientCreatedTemplate(adminName, clientId, clientName, jdRole, candidatesNeeded, source);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            // Fallback to plain text
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("New Client Position - Action Required");
            message.setText(
                "Hi " + (adminName != null ? adminName : "Admin") + ",\n\n" +
                "A new client position has been created that requires " + source + " candidates:\n\n" +
                "Client: " + clientName + "\n" +
                "Role: " + jdRole + "\n" +
                "Candidates Needed: " + candidatesNeeded + "\n\n" +
                "Please review the position and trigger AI matching for suitable candidates.\n\n" +
                "Review link: " + interviewBaseUrl.replace("/interview", "") + "/admin/clients\n\n" +
                "Bench Readiness Team"
            );
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
            
            String htmlContent = buildCancellationEmailTemplate(name, interviewId, jdTitle, reason);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            // Fallback to plain text if HTML fails
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("Interview Cancelled - Bench Readiness");
            message.setText(
                "Hi " + name + ",\n\n" +
                "We regret to inform you that your scheduled interview has been cancelled.\n\n" +
                "Interview ID: " + interviewId + "\n" +
                (jdTitle != null ? "Position: " + jdTitle + "\n" : "") +
                (reason != null ? "Reason: " + reason + "\n" : "") +
                "\nIf you have any questions, please contact our recruitment team.\n\n" +
                "Best regards,\n" +
                "Bench Readiness Team"
            );
            mailSender.send(message);
        }
    }

    private String buildCancellationEmailTemplate(String candidateName, String interviewId, String jdTitle, String reason) {
        return buildEmailTemplate(
            "Interview Cancelled",
            "#e53e3e",
            candidateName,
            "We regret to inform you that your scheduled technical interview has been <strong style='color: #e53e3e;'>cancelled</strong>.",
            buildDetailsCard("Interview Details", Map.of(
                "Interview ID", interviewId,
                "Position", jdTitle != null ? jdTitle : "N/A",
                "Reason", reason != null ? reason : "Administrative decision"
            ), "#e53e3e"),
            "If you have any questions or would like to reschedule, please contact our recruitment team.",
            "Contact Support",
            "mailto:" + from
        );
    }

    private String buildInterviewInviteTemplate(String candidateName, String interviewId) {
        String interviewLink = interviewBaseUrl + "/" + interviewId;
        String currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
        
        return buildEmailTemplate(
            "Interview Scheduled",
            "#10b981",
            candidateName,
            "Your technical interview has been successfully scheduled. We're excited to learn more about your skills and experience!",
            buildDetailsCard("Interview Details", Map.of(
                "Interview ID", interviewId,
                "Scheduled Date", currentDate,
                "Status", "Ready to Start"
            ), "#10b981") +
            "<div style='background-color: #f0fdf4; border: 2px solid #10b981; padding: 20px; margin: 25px 0; border-radius: 8px; text-align: center;'>" +
            "<p style='margin: 0 0 15px; color: #065f46; font-size: 14px; font-weight: 600;'>INTERVIEW ACCESS LINK</p>" +
            "<a href='" + interviewLink + "' style='display: inline-block; background: linear-gradient(135deg, #10b981 0%, #059669 100%); color: #ffffff; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: 600; font-size: 15px; box-shadow: 0 4px 6px rgba(16, 185, 129, 0.3);'>" +
            "Start Interview" +
            "</a>" +
            "<p style='margin: 15px 0 0; color: #6b7280; font-size: 12px;'>Or copy this link: <br><span style='color: #10b981; word-break: break-all;'>" + interviewLink + "</span></p>" +
            "</div>",
            "<strong>Important:</strong> Log in with your registered credentials to begin. Make sure you're in a quiet environment with a stable internet connection. Good luck!",
            "View Dashboard",
            interviewBaseUrl.replace("/interview", "") + "/candidate/dashboard"
        );
    }

    private String buildInterviewAbandonedTemplate(String managerName, String interviewId, String reason) {
        String reviewLink = interviewBaseUrl.replace("/interview", "") + "/admin/interviews/" + interviewId + "/review";
        String reasonText = "not_prepared".equals(reason) 
            ? "Candidate indicated they are not prepared" 
            : "Interview time limit reached";
        String statusColor = "not_prepared".equals(reason) ? "#f59e0b" : "#ef4444";
        
        return buildEmailTemplate(
            "Interview Ended Early",
            statusColor,
            managerName,
            "An interview has ended before completion. Please review the transcript and provide feedback.",
            buildDetailsCard("Interview Details", Map.of(
                "Interview ID", interviewId,
                "Status", "Ended Early",
                "Reason", reasonText,
                "Action Required", "Review & Sign-off"
            ), statusColor),
            "Please review the interview transcript and candidate responses to provide appropriate feedback and sign-off.",
            "Review Interview",
            reviewLink
        );
    }

    private String buildClientCreatedTemplate(String adminName, String clientId, String clientName, 
                                            String jdRole, Integer candidatesNeeded, String source) {
        String clientLink = interviewBaseUrl.replace("/interview", "") + "/admin/clients";
        
        return buildEmailTemplate(
            "New Client Position",
            "#3b82f6",
            adminName,
            "A new client position has been created and requires your attention for candidate matching.",
            buildDetailsCard("Position Details", Map.of(
                "Client", clientName,
                "Role", jdRole,
                "Candidates Needed", candidatesNeeded.toString(),
                "Source", source,
                "Status", "Awaiting Candidate Match"
            ), "#3b82f6"),
            "Please review the position requirements and trigger AI-powered candidate matching to find suitable candidates from your pool.",
            "View Client & Match Candidates",
            clientLink
        );
    }

    private String buildEmailTemplate(String title, String accentColor, String recipientName, 
                                     String introText, String contentHtml, String footerText, 
                                     String buttonText, String buttonLink) {
        return "<!DOCTYPE html>" +
            "<html lang='en'>" +
            "<head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<title>" + title + "</title>" +
            "</head>" +
            "<body style='margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif; background-color: #f5f5f5;'>" +
            "<table width='100%' cellpadding='0' cellspacing='0' style='background-color: #f5f5f5; padding: 40px 20px;'>" +
            "<tr><td align='center'>" +
            "<table width='600' cellpadding='0' cellspacing='0' style='background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); overflow: hidden;'>" +
            
            "<!-- Header with Logo -->" +
            "<tr><td style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 40px 30px; text-align: center;'>" +
            "<div style='background-color: rgba(255,255,255,0.95); display: inline-block; padding: 15px 30px; border-radius: 50px; margin-bottom: 20px;'>" +
            "<h1 style='margin: 0; color: #667eea; font-size: 28px; font-weight: 700; letter-spacing: -0.5px;'>" +
            "<span style='color: #764ba2;'>●</span> Bench Readiness" +
            "</h1>" +
            "</div>" +
            "<h2 style='margin: 0; color: #ffffff; font-size: 24px; font-weight: 600;'>" + title + "</h2>" +
            "</td></tr>" +
            
            "<!-- Content -->" +
            "<tr><td style='padding: 40px 30px;'>" +
            "<p style='margin: 0 0 20px; color: #333333; font-size: 16px; line-height: 1.6;'>" +
            "Hi <strong>" + recipientName + "</strong>," +
            "</p>" +
            "<p style='margin: 0 0 25px; color: #555555; font-size: 15px; line-height: 1.6;'>" +
            introText +
            "</p>" +
            
            contentHtml +
            
            "<p style='margin: 25px 0 20px; color: #555555; font-size: 15px; line-height: 1.6;'>" +
            footerText +
            "</p>" +
            
            "<!-- Action Button -->" +
            "<div style='text-align: center; margin: 30px 0;'>" +
            "<a href='" + buttonLink + "' style='display: inline-block; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #ffffff; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: 600; font-size: 15px; box-shadow: 0 4px 6px rgba(102, 126, 234, 0.3);'>" +
            buttonText +
            "</a>" +
            "</div>" +
            "</td></tr>" +
            
            "<!-- Footer -->" +
            "<tr><td style='background-color: #f7fafc; padding: 30px; text-align: center; border-top: 1px solid #e2e8f0;'>" +
            "<p style='margin: 0 0 10px; color: #2d3748; font-size: 16px; font-weight: 600;'>Best regards,</p>" +
            "<p style='margin: 0 0 15px; color: #667eea; font-size: 18px; font-weight: 700;'>Bench Readiness Team</p>" +
            "<p style='margin: 0 0 10px; color: #718096; font-size: 13px;'>AI-powered technical interview platform</p>" +
            "<p style='margin: 0; color: #a0aec0; font-size: 12px;'>" +
            "Need help? Contact us at <a href='mailto:" + from + "' style='color: #667eea; text-decoration: none;'>" + from + "</a>" +
            "</p>" +
            "</td></tr>" +
            
            "</table>" +
            "</td></tr>" +
            "</table>" +
            "</body>" +
            "</html>";
    }

    private String buildDetailsCard(String cardTitle, Map<String, String> details, String borderColor) {
        StringBuilder rows = new StringBuilder();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            rows.append(
                "<tr><td style='color: #718096; font-size: 14px; padding: 8px 0; border-bottom: 1px solid #e2e8f0;'>" + entry.getKey() + ":</td>" +
                "<td style='color: #2d3748; font-size: 14px; font-weight: 600; padding: 8px 0; text-align: right; border-bottom: 1px solid #e2e8f0;'>" + entry.getValue() + "</td></tr>"
            );
        }
        
        return "<div style='background-color: #f7fafc; border-left: 4px solid " + borderColor + "; padding: 20px; margin: 25px 0; border-radius: 8px;'>" +
            "<p style='margin: 0 0 15px; color: #2d3748; font-size: 14px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;'>" + cardTitle + "</p>" +
            "<table width='100%' cellpadding='0' cellspacing='0'>" +
            rows.toString() +
            "</table>" +
            "</div>";
    }
}
