package com.benchreadiness.ops.observer.service;

import com.benchreadiness.ops.observer.client.AuthServiceClient;
import com.benchreadiness.ops.observer.dto.ClientCreatedRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AuthServiceClient authServiceClient;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.interview-base-url}")
    private String interviewBaseUrl;

    @Value("${app.completion-notify.enabled:true}")
    private boolean completionNotifyEnabled;

    public EmailService(JavaMailSender mailSender, AuthServiceClient authServiceClient) {
        this.mailSender = mailSender;
        this.authServiceClient = authServiceClient;
    }

    private void sendEmail(String toEmail, String subject, String html) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (MessagingException e) {
            throw new RuntimeException("Mail send error: " + e.getMessage(), e);
        }
    }

    public void sendInterviewInvite(String toEmail, String candidateName, String interviewId) {
        String name = (candidateName != null && !candidateName.isBlank()) ? candidateName : "Candidate";
        sendEmail(toEmail, "Your Technical Interview is Scheduled - Bench Readiness",
                buildInterviewInviteTemplate(name, interviewId));
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

        sendEmail(managerEmail, abandonedSubject(reason),
                buildInterviewAbandonedTemplate(managerName, interviewId, reason));
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

    public void sendClientCreatedNotification(ClientCreatedRequest req) {
        try {
            List<Map<String, Object>> recipients = authServiceClient.getClientNotificationRecipients();
            if (recipients == null || recipients.isEmpty()) {
                log.warn("No admin/recruiter recipients found for client creation notification");
                return;
            }

            Set<String> sentEmails = new HashSet<>();
            for (Map<String, Object> recipient : recipients) {
                String email = (String) recipient.get("email");
                if (email == null || email.isBlank() || !sentEmails.add(email.trim().toLowerCase())) {
                    continue;
                }
                String name = (String) recipient.getOrDefault("name", "Team");
                try {
                    sendClientNotificationEmail(name, email, req);
                    log.info("Client creation notification sent to {}", email);
                } catch (Exception e) {
                    log.warn("Failed to send client notification to {}: {}", email, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send client creation notifications: {}", e.getMessage());
        }
    }

    private void sendClientNotificationEmail(String recipientName, String toEmail, ClientCreatedRequest req) {
        sendEmail(toEmail, "New Client Added — " + safe(req.getClientName()) + " (" + safe(req.getJdRole()) + ")",
                buildClientCreatedTemplate(recipientName, req));
    }

    /**
     * Sends a review-required notification to every admin and recruiter whose branch
     * matches the interview's branch. SUPER_ADMINs (branch == null) receive it for
     * every branch. DEVELOPMENT interviews go only to DEVELOPMENT staff, TESTING
     * interviews go only to TESTING staff.
     */
    public void sendInterviewCompletedNotification(Map<String, String> req) {
        if (!completionNotifyEnabled) {
            log.debug("Interview completion notifications disabled (app.completion-notify.enabled=false)");
            return;
        }

        String interviewId    = req.getOrDefault("interviewId", "");
        String branch         = req.getOrDefault("branch", "DEVELOPMENT");
        String candidateName  = req.getOrDefault("candidateName", "Candidate");
        String status         = req.getOrDefault("status", "COMPLETED");
        String proposedVerdict = req.getOrDefault("proposedVerdict", "UNKNOWN");
        String reason         = req.get("reason"); // only present for WITHDRAWN

        List<Map<String, Object>> allStaff;
        try {
            allStaff = authServiceClient.getAllStaff();
        } catch (Exception e) {
            log.error("Cannot fetch staff list for interview completion notification (interview={}): {}", interviewId, e.getMessage());
            return;
        }
        if (allStaff == null || allStaff.isEmpty()) {
            log.warn("No staff found — skipping completion notification for interview {}", interviewId);
            return;
        }

        // Safety cap: never send more than 20 emails from a single interview event.
        // If you have more than 20 staff, increase APP_COMPLETION_NOTIFY_RECIPIENT_LIMIT.
        final int RECIPIENT_CAP = 20;

        boolean isWithdrawn = "WITHDRAWN".equalsIgnoreCase(status);
        String subject = buildCompletionSubject(isWithdrawn, candidateName, branch, reason);
        String reviewLink = interviewBaseUrl.replace("/interview", "") + "/admin/interviews/" + interviewId + "/review";

        Set<String> sentEmails = new HashSet<>();
        int sent = 0;
        for (Map<String, Object> staff : allStaff) {
            if (sent >= RECIPIENT_CAP) {
                log.warn("Completion notification recipient cap ({}) reached for interview {} — remaining staff skipped", RECIPIENT_CAP, interviewId);
                break;
            }
            String recipientEmail = (String) staff.get("email");
            if (recipientEmail == null || recipientEmail.isBlank()) continue;
            if (!sentEmails.add(recipientEmail.trim().toLowerCase())) continue;

            // Branch segregation: null branch = SUPER_ADMIN (receives all), otherwise must match
            String recipientBranch = (String) staff.get("branch");
            if (recipientBranch != null && !recipientBranch.equalsIgnoreCase(branch)) continue;

            String recipientName = (String) staff.getOrDefault("name", "Team");
            try {
                sendEmail(recipientEmail, subject, buildCompletionEmailBody(recipientName, interviewId,
                        candidateName, branch, status, proposedVerdict, reason, reviewLink));
                sent++;
                log.info("Interview completion notification sent to {} [branch={}] for interview {}", recipientEmail, branch, interviewId);
            } catch (Exception e) {
                log.warn("Failed to send completion notification to {} for interview {}: {}", recipientEmail, interviewId, e.getMessage());
            }
        }
    }

    private String buildCompletionSubject(boolean isWithdrawn, String candidateName, String branch, String reason) {
        String branchTag = "[" + branch + "]";
        if (isWithdrawn) {
            String reasonLabel = switch (reason != null ? reason : "") {
                case "tab_switch_violation" -> "Tab Switch Violation";
                case "ai_manipulation"      -> "AI Manipulation Detected";
                case "proctoring_violation" -> "Proctoring Violation";
                case "not_prepared"         -> "Candidate Not Prepared";
                default                     -> "Ended Early";
            };
            return branchTag + " Interview Withdrawn — " + reasonLabel + " | Review Required: " + candidateName;
        }
        return branchTag + " Interview Completed — Review Required: " + candidateName;
    }

    private String buildCompletionEmailBody(String recipientName, String interviewId,
                                            String candidateName, String branch, String status,
                                            String proposedVerdict, String reason, String reviewLink) {
        boolean isWithdrawn = "WITHDRAWN".equalsIgnoreCase(status);
        String accentColor  = isWithdrawn ? "#dc2626" : "#7c3aed";
        String title        = isWithdrawn ? "Interview Ended — Review Required" : "Interview Completed — Review Required";
        String branchLabel  = "TESTING".equalsIgnoreCase(branch) ? "Testing" : "Development";
        String verdictDisplay = proposedVerdict.replace("_", " ");

        String reasonBlock = "";
        if (isWithdrawn && reason != null) {
            String reasonText = switch (reason) {
                case "tab_switch_violation" -> "Candidate switched tabs or windows multiple times (automatic termination).";
                case "ai_manipulation"      -> "AI manipulation was detected and the session was terminated automatically.";
                case "proctoring_violation" -> "A video proctoring violation was detected (automatic termination).";
                case "not_prepared"         -> "The candidate indicated they were not prepared to continue.";
                default                     -> "The interview time limit was reached.";
            };
            reasonBlock = "<div style='background:#fef2f2;border-left:4px solid #dc2626;padding:14px 16px;margin:16px 0;border-radius:6px;'>"
                    + "<p style='margin:0;color:#991b1b;font-size:13px;font-weight:600;'>Termination Reason</p>"
                    + "<p style='margin:6px 0 0;color:#7f1d1d;font-size:13px;'>" + escapeHtml(reasonText) + "</p></div>";
        }

        Map<String, String> details = new java.util.LinkedHashMap<>();
        details.put("Interview ID",    interviewId);
        details.put("Candidate",       candidateName);
        details.put("Branch",          branchLabel);
        details.put("Status",          status.replace("_", " "));
        details.put("Proposed Verdict", verdictDisplay);
        details.put("Action Required", "Review transcript and sign off");

        String intro = isWithdrawn
                ? "An interview session in the <strong>" + branchLabel + "</strong> branch has <strong style='color:#dc2626;'>ended early</strong>. The AI assessment has been recorded and is ready for your review."
                : "An interview session in the <strong>" + branchLabel + "</strong> branch has been <strong style='color:#7c3aed;'>completed</strong>. The AI assessment is ready for your sign-off.";

        String footer = "Please review the full transcript, assessment scores, and proctoring timeline before signing off.";

        return buildEmailTemplate(title, accentColor, recipientName, intro,
                buildDetailsCard("Interview Summary", details, accentColor) + reasonBlock,
                footer, "Review Interview", reviewLink);
    }

    public void sendInterviewCancellation(String toEmail, String candidateName, String interviewId, String jdTitle, String reason) {
        String name = (candidateName != null && !candidateName.isBlank()) ? candidateName : "Candidate";
        sendEmail(toEmail, "Interview Cancelled - Bench Readiness",
                buildCancellationEmailTemplate(name, interviewId, jdTitle, reason));
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

    private String buildClientCreatedTemplate(String adminName, ClientCreatedRequest req) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Client", safe(req.getClientName()));
        details.put("Role", safe(req.getJdRole()));
        details.put("Positions Vacant", safeInt(req.getPositionsVacant()));
        details.put("Bench/B2B Candidates Needed", safeInt(req.getBenchB2bCandidatesNeeded()));
        details.put("Market Candidates Needed", safeInt(req.getMarketCandidatesNeeded()));
        if (req.getJdFileName() != null && !req.getJdFileName().isBlank()) {
            details.put("JD File", req.getJdFileName());
        }

        String jdBlock = req.getJdDescription() != null && !req.getJdDescription().isBlank()
            ? "<div style='background:#f7fafc;border:1px solid #e2e8f0;padding:16px;margin:20px 0;border-radius:8px;'>"
                + "<p style='margin:0 0 8px;color:#2d3748;font-size:14px;font-weight:600;'>Job Description</p>"
                + "<p style='margin:0;color:#4a5568;font-size:14px;line-height:1.6;white-space:pre-wrap;'>"
                + escapeHtml(req.getJdDescription()) + "</p></div>"
            : "";

        String requirementsBlock = req.getSkillRequirementsSummary() != null && !req.getSkillRequirementsSummary().isBlank()
            ? "<div style='background:#eff6ff;border-left:4px solid #3b82f6;padding:16px;margin:20px 0;border-radius:8px;'>"
                + "<p style='margin:0 0 8px;color:#1e3a8a;font-size:14px;font-weight:600;'>Skill &amp; Position Requirements</p>"
                + "<pre style='margin:0;color:#1e40af;font-size:13px;line-height:1.6;white-space:pre-wrap;font-family:inherit;'>"
                + escapeHtml(req.getSkillRequirementsSummary()) + "</pre></div>"
            : "";

        return buildEmailTemplate("New Client Added", "#3b82f6", safe(adminName),
            "A new client has been added to the platform. Review the job description and requirements below, then start candidate matching when ready.",
            buildDetailsCard("Client Overview", details, "#3b82f6") + jdBlock + requirementsBlock,
            "All admins and recruiters have been notified. Please review the client and trigger matching if applicable.",
            "View Client", interviewBaseUrl.replace("/interview", "") + "/admin/clients");
    }

    private String safe(String value) {
        return value != null && !value.isBlank() ? value : "N/A";
    }

    private String safeInt(Integer value) {
        return value != null ? value.toString() : "0";
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
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
