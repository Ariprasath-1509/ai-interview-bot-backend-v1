package com.benchreadiness.observer.service;

import com.benchreadiness.observer.client.AuthServiceClient;
import com.benchreadiness.observer.client.InterviewServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class DigestService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DigestService.class);

    private final JavaMailSender mailSender;
    private final AuthServiceClient authServiceClient;
    private final InterviewServiceClient interviewServiceClient;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.interview-base-url}")
    private String interviewBaseUrl;

    public DigestService(JavaMailSender mailSender, AuthServiceClient authServiceClient, InterviewServiceClient interviewServiceClient) {
        this.mailSender = mailSender;
        this.authServiceClient = authServiceClient;
        this.interviewServiceClient = interviewServiceClient;
    }

    @Scheduled(cron = "${app.digest.cron}")
    public void sendDailyDigest() {
        log.info("Sending daily interview digest...");
        try {
            // 1. Get all super_admin + admin emails
            List<Map<String, String>> recipients = fetchRecipients();
            if (recipients.isEmpty()) {
                log.warn("No SUPER_ADMIN or ADMIN users found — skipping digest");
                return;
            }

            // 2. Get today's interviews
            List<Map<String, Object>> interviews = fetchTodaysInterviews();

            // 3. Build and send email
            String subject = buildSubject(interviews.size());
            String body = buildBody(interviews);

            for (Map<String, String> recipient : recipients) {
                try {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(from);
                    message.setTo(recipient.get("email"));
                    message.setSubject(subject);
                    message.setText("Hi " + recipient.getOrDefault("name", "Team") + ",\n\n" + body);
                    mailSender.send(message);
                } catch (Exception e) {
                    log.warn("Failed to send digest to {}: {}", recipient.get("email"), e.getMessage());
                }
            }
            log.info("Daily digest sent to {} recipients", recipients.size());
        } catch (Exception e) {
            log.error("Daily digest failed: {}", e.getMessage());
        }
    }

    private List<Map<String, String>> fetchRecipients() {
        try {
            return authServiceClient.getAdmins();
        } catch (Exception e) {
            log.warn("Failed to fetch admin list: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> fetchTodaysInterviews() {
        try {
            return interviewServiceClient.getTodaysInterviews();
        } catch (Exception e) {
            log.warn("Failed to fetch today's interviews: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildSubject(int count) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        return count == 0
            ? "Daily Interview Digest — " + date + " (No interviews today)"
            : "Daily Interview Digest — " + date + " (" + count + " interview" + (count > 1 ? "s" : "") + ")";
    }

    private String buildBody(List<Map<String, Object>> interviews) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        String reviewBaseUrl = interviewBaseUrl.replace("/interview", "");

        if (interviews.isEmpty()) {
            return "No interviews were scheduled or conducted today (" + date + ").\n\n" +
                   "Log in to the platform to view historical data:\n" +
                   reviewBaseUrl + "/admin/review\n\n" +
                   "— Bench Readiness Platform";
        }

        // Count by status
        long signedOff    = interviews.stream().filter(i -> "SIGNED_OFF".equals(i.get("status"))).count();
        long reviewPending = interviews.stream().filter(i -> "REVIEW_PENDING".equals(i.get("status"))).count();
        long completed    = interviews.stream().filter(i -> "COMPLETED".equals(i.get("status"))).count();
        long withdrawn    = interviews.stream().filter(i -> "WITHDRAWN".equals(String.valueOf(i.getOrDefault("proposedVerdict", "")))).count();
        long scheduled    = interviews.stream().filter(i -> "SCHEDULED".equals(i.get("status"))).count();

        StringBuilder sb = new StringBuilder();
        sb.append("Today's Interview Summary — ").append(date).append("\n");
        sb.append("Total: ").append(interviews.size()).append(" interview").append(interviews.size() > 1 ? "s" : "").append("\n\n");

        // Table header
        sb.append(String.format("%-25s %-30s %-18s %-20s%n",
            "Candidate", "Role", "Status", "Verdict"));
        sb.append("-".repeat(95)).append("\n");

        for (Map<String, Object> i : interviews) {
            String candidate = truncate(String.valueOf(i.getOrDefault("candidateName", "Unknown")), 24);
            String role      = truncate(String.valueOf(i.getOrDefault("jdTitle", "-")), 29);
            String status    = truncate(String.valueOf(i.getOrDefault("status", "-")), 17);
            String verdict   = i.get("finalVerdict") != null
                ? String.valueOf(i.get("finalVerdict"))
                : i.get("proposedVerdict") != null
                    ? String.valueOf(i.get("proposedVerdict")) + " (proposed)"
                    : "Pending";
            sb.append(String.format("%-25s %-30s %-18s %-20s%n", candidate, role, status, verdict));
        }

        sb.append("\n");
        sb.append("Summary:\n");
        sb.append("  Signed off:     ").append(signedOff).append("\n");
        sb.append("  Review pending: ").append(reviewPending).append("\n");
        sb.append("  Completed:      ").append(completed).append("\n");
        sb.append("  Withdrawn:      ").append(withdrawn).append("\n");
        sb.append("  Scheduled:      ").append(scheduled).append("\n\n");
        sb.append("View full details: ").append(reviewBaseUrl).append("/admin/review\n\n");
        sb.append("— Bench Readiness Platform");

        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
