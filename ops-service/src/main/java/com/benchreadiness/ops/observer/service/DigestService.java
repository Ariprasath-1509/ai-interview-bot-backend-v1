package com.benchreadiness.ops.observer.service;

import com.benchreadiness.ops.branch.BranchSegregation;
import com.benchreadiness.ops.observer.client.AuthServiceClient;
import com.benchreadiness.ops.observer.client.InterviewServiceClient;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    public DigestService(JavaMailSender mailSender, AuthServiceClient authServiceClient,
                         InterviewServiceClient interviewServiceClient) {
        this.mailSender = mailSender;
        this.authServiceClient = authServiceClient;
        this.interviewServiceClient = interviewServiceClient;
    }

    @Scheduled(cron = "${app.digest.cron}")
    public void sendDailyDigest() {
        log.info("Sending daily platform report...");
        if (from == null || from.isBlank()) {
            log.error("Daily digest skipped: spring.mail.username is not configured");
            return;
        }
        try {
            List<Map<String, Object>> recipients = authServiceClient.getAdmins();
            if (recipients.isEmpty()) {
                log.warn("No SUPER_ADMIN or ADMIN users found — skipping digest");
                return;
            }

            for (Map<String, Object> recipient : recipients) {
                String email = recipient.get("email") != null ? recipient.get("email").toString() : null;
                if (email == null || email.isBlank()) {
                    log.warn("Skipping admin with no email address");
                    continue;
                }
                String branch = recipient.get("branch") != null ? recipient.get("branch").toString() : null;
                String digestBranch = null;
                if (BranchSegregation.isEnabled() && !"SUPER_ADMIN".equals(recipient.get("role"))) {
                    digestBranch = branch;
                }

                Map<String, Object> reportData = interviewServiceClient.getDailyReportDataForBranch(digestBranch);

                Map<String, Object> pipelineStatus = null;
                try {
                    pipelineStatus = authServiceClient.getCandidatePipelineStatus(digestBranch);
                } catch (Exception e) {
                    log.warn("Failed to fetch pipeline status for {}: {}", email, e.getMessage());
                }

                String subject = buildSubject(reportData, digestBranch);
                String htmlBody = buildHtmlReport(reportData, pipelineStatus, digestBranch);

                try {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                    helper.setFrom(from);
                    helper.setTo(email);
                    helper.setSubject(subject);
                    helper.setText(htmlBody, true);
                    mailSender.send(message);
                    log.info("Daily report sent to: {} (branch={})", email, digestBranch != null ? digestBranch : "ALL");
                } catch (Exception e) {
                    log.warn("Failed to send report to {}: {}", email, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Daily digest failed: {}", e.getMessage(), e);
        }
    }

    private String buildSubject(Map<String, Object> reportData, String branch) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) reportData.get("interviewMetrics");
        int total = metrics != null ? ((Number) metrics.getOrDefault("totalToday", 0)).intValue() : 0;
        @SuppressWarnings("unchecked")
        Map<String, Object> violations = (Map<String, Object>) reportData.get("violations");
        int withdrawn = violations != null ? ((Number) violations.getOrDefault("totalWithdrawn", 0)).intValue() : 0;
        String branchLabel = branch != null ? " [" + branch + "]" : "";
        String base = "Daily Platform Report" + branchLabel + " — " + date + " (" + total + " interview" + (total != 1 ? "s" : "") + ")";
        if (withdrawn > 0) base += " ⚠️ " + withdrawn + " violation" + (withdrawn != 1 ? "s" : "");
        return base;
    }

    @SuppressWarnings("unchecked")
    private String buildHtmlReport(Map<String, Object> reportData, Map<String, Object> pipelineStatus,
                                   String branch) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        String dashboardUrl = interviewBaseUrl.replace("/interview", "") + "/admin/review";
        String branchBanner = branch != null
                ? "<p style='margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:13px;font-weight:600;'>Branch: " + esc(branch) + "</p>"
                : "";

        Map<String, Object> metrics = (Map<String, Object>) reportData.getOrDefault("interviewMetrics", Map.of());
        Map<String, Object> violations = (Map<String, Object>) reportData.getOrDefault("violations", Map.of());
        Map<String, Object> alerts = (Map<String, Object>) reportData.getOrDefault("alerts", Map.of());
        List<Map<String, Object>> interviewList = (List<Map<String, Object>>) reportData.getOrDefault("interviewList", List.of());

        int totalToday = ((Number) metrics.getOrDefault("totalToday", 0)).intValue();
        int scheduled = ((Number) metrics.getOrDefault("scheduled", 0)).intValue();
        int inProgress = ((Number) metrics.getOrDefault("inProgress", 0)).intValue();
        int completed = ((Number) metrics.getOrDefault("completed", 0)).intValue();
        int reviewPending = ((Number) metrics.getOrDefault("reviewPending", 0)).intValue();
        int signedOff = ((Number) metrics.getOrDefault("signedOff", 0)).intValue();
        int readyCount = ((Number) metrics.getOrDefault("readyCount", 0)).intValue();
        double successRate = ((Number) metrics.getOrDefault("successRate", 0)).doubleValue();

        int totalWithdrawn = ((Number) violations.getOrDefault("totalWithdrawn", 0)).intValue();
        List<Map<String, Object>> violationDetails = (List<Map<String, Object>>) violations.getOrDefault("details", List.of());

        int pendingReviewCount = ((Number) alerts.getOrDefault("pendingReviewCount", 0)).intValue();
        List<Map<String, Object>> pendingReviews = (List<Map<String, Object>>) alerts.getOrDefault("pendingReviews", List.of());

        int rfd = 0, wfd = 0, dob = 0, deployed = 0, totalCandidates = 0, todayRegistrations = 0;
        if (pipelineStatus != null) {
            rfd = ((Number) pipelineStatus.getOrDefault("rfd", 0)).intValue();
            wfd = ((Number) pipelineStatus.getOrDefault("wfd", 0)).intValue();
            dob = ((Number) pipelineStatus.getOrDefault("dob", 0)).intValue();
            deployed = ((Number) pipelineStatus.getOrDefault("deployed", 0)).intValue();
            totalCandidates = ((Number) pipelineStatus.getOrDefault("totalCandidates", 0)).intValue();
            todayRegistrations = ((Number) pipelineStatus.getOrDefault("todayRegistrations", 0)).intValue();
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><title>Daily Platform Report</title></head>")
            .append("<body style='margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,Arial,sans-serif;background:#f0f2f5;'>")
            .append("<table width='100%' cellpadding='0' cellspacing='0' style='background:#f0f2f5;padding:30px 15px;'><tr><td align='center'>")
            .append("<table width='640' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>");

        // Header
        html.append("<tr><td style='background:linear-gradient(135deg,#1e3a5f 0%,#2d6a9f 100%);padding:32px 30px;text-align:center;'>")
            .append("<h1 style='margin:0;color:#ffffff;font-size:26px;font-weight:700;'>Daily Platform Report</h1>")
            .append("<p style='margin:8px 0 0;color:rgba(255,255,255,0.75);font-size:14px;'>").append(date).append("</p>")
            .append(branchBanner)
            .append("</td></tr>");

        // Alerts banner
        if (totalWithdrawn > 0 || pendingReviewCount > 0) {
            html.append("<tr><td style='background:#fff3cd;border-bottom:3px solid #ffc107;padding:14px 30px;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0'><tr>");
            if (totalWithdrawn > 0)
                html.append("<td style='color:#856404;font-size:13px;font-weight:600;'>⚠️ ").append(totalWithdrawn).append(" violation").append(totalWithdrawn > 1 ? "s" : "").append(" today</td>");
            if (pendingReviewCount > 0)
                html.append("<td style='color:#856404;font-size:13px;font-weight:600;text-align:right;'>🕐 ").append(pendingReviewCount).append(" pending review &gt;24h</td>");
            html.append("</tr></table></td></tr>");
        }

        // Summary cards
        html.append("<tr><td style='padding:28px 30px 10px;'>")
            .append("<p style='margin:0 0 16px;font-size:11px;font-weight:700;color:#6b7280;text-transform:uppercase;letter-spacing:1px;'>Executive Summary</p>")
            .append("<table width='100%' cellpadding='0' cellspacing='0'><tr>")
            .append(summaryCard("Interviews Today", String.valueOf(totalToday), "#2d6a9f", "#e8f0fe"))
            .append(summaryCard("Ready", String.valueOf(readyCount), "#059669", "#d1fae5"))
            .append(summaryCard("Success Rate", String.format("%.0f%%", successRate), successRate >= 70 ? "#059669" : "#d97706", successRate >= 70 ? "#d1fae5" : "#fef3c7"))
            .append(summaryCard("Violations", String.valueOf(totalWithdrawn), totalWithdrawn > 0 ? "#dc2626" : "#6b7280", totalWithdrawn > 0 ? "#fee2e2" : "#f3f4f6"))
            .append("</tr></table></td></tr>");

        // Status breakdown
        html.append("<tr><td style='padding:10px 30px 20px;'>")
            .append(sectionTitle("📊 Interview Status Breakdown"))
            .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;'>")
            .append(statusRow("Scheduled", scheduled, "#3b82f6", true))
            .append(statusRow("In Progress", inProgress, "#f59e0b", false))
            .append(statusRow("Completed", completed, "#8b5cf6", true))
            .append(statusRow("Review Pending", reviewPending, "#ef4444", false))
            .append(statusRow("Signed Off", signedOff, "#10b981", true))
            .append(statusRow("Withdrawn / Violations", totalWithdrawn, "#dc2626", false))
            .append("</table></td></tr>");

        // Today's interviews
        if (!interviewList.isEmpty()) {
            html.append("<tr><td style='padding:10px 30px 20px;'>").append(sectionTitle("📋 Today's Interviews"))
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;font-size:13px;'>")
                .append("<tr style='background:#f9fafb;'>")
                .append("<th style='padding:10px 12px;text-align:left;color:#374151;font-weight:600;border-bottom:1px solid #e5e7eb;'>Candidate</th>")
                .append("<th style='padding:10px 12px;text-align:left;color:#374151;font-weight:600;border-bottom:1px solid #e5e7eb;'>Role</th>")
                .append("<th style='padding:10px 12px;text-align:center;color:#374151;font-weight:600;border-bottom:1px solid #e5e7eb;'>Mode</th>")
                .append("<th style='padding:10px 12px;text-align:center;color:#374151;font-weight:600;border-bottom:1px solid #e5e7eb;'>Status</th>")
                .append("<th style='padding:10px 12px;text-align:center;color:#374151;font-weight:600;border-bottom:1px solid #e5e7eb;'>Verdict</th></tr>");
            for (int i = 0; i < interviewList.size(); i++) {
                Map<String, Object> interview = interviewList.get(i);
                String bg = i % 2 == 0 ? "#ffffff" : "#f9fafb";
                String status = String.valueOf(interview.getOrDefault("status", "-"));
                String verdict = interview.get("finalVerdict") != null ? String.valueOf(interview.get("finalVerdict"))
                    : interview.get("proposedVerdict") != null ? String.valueOf(interview.get("proposedVerdict")) + "*" : "Pending";
                html.append("<tr style='background:").append(bg).append(";'>")
                    .append("<td style='padding:9px 12px;color:#111827;border-bottom:1px solid #f3f4f6;'>").append(esc(String.valueOf(interview.getOrDefault("candidateName", "Unknown")))).append("</td>")
                    .append("<td style='padding:9px 12px;color:#374151;border-bottom:1px solid #f3f4f6;'>").append(esc(String.valueOf(interview.getOrDefault("jdTitle", "-")))).append("</td>")
                    .append("<td style='padding:9px 12px;text-align:center;border-bottom:1px solid #f3f4f6;'>").append(modeBadge(String.valueOf(interview.getOrDefault("mode", "-")))).append("</td>")
                    .append("<td style='padding:9px 12px;text-align:center;border-bottom:1px solid #f3f4f6;'>").append(statusBadge(status)).append("</td>")
                    .append("<td style='padding:9px 12px;text-align:center;border-bottom:1px solid #f3f4f6;'>").append(verdictBadge(verdict)).append("</td></tr>");
            }
            html.append("</table><p style='margin:6px 0 0;font-size:11px;color:#9ca3af;'>* proposed verdict, pending admin sign-off</p></td></tr>");
        }

        // Violations
        if (!violationDetails.isEmpty()) {
            html.append("<tr><td style='padding:10px 30px 20px;'>").append(sectionTitle("🚨 Integrity Violations"))
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #fca5a5;border-radius:8px;overflow:hidden;font-size:13px;background:#fff5f5;'>")
                .append("<tr style='background:#fee2e2;'><th style='padding:10px 12px;text-align:left;color:#991b1b;font-weight:600;border-bottom:1px solid #fca5a5;'>Candidate</th>")
                .append("<th style='padding:10px 12px;text-align:left;color:#991b1b;font-weight:600;border-bottom:1px solid #fca5a5;'>Reason</th>")
                .append("<th style='padding:10px 12px;text-align:left;color:#991b1b;font-weight:600;border-bottom:1px solid #fca5a5;'>Time</th></tr>");
            for (Map<String, Object> v : violationDetails) {
                html.append("<tr><td style='padding:9px 12px;color:#111827;border-bottom:1px solid #fee2e2;'>").append(esc(String.valueOf(v.getOrDefault("candidateName", "Unknown")))).append("</td>")
                    .append("<td style='padding:9px 12px;color:#dc2626;font-weight:600;border-bottom:1px solid #fee2e2;'>").append(esc(String.valueOf(v.getOrDefault("reason", "Unknown")))).append("</td>")
                    .append("<td style='padding:9px 12px;color:#6b7280;font-size:12px;border-bottom:1px solid #fee2e2;'>").append(formatTimestamp(String.valueOf(v.getOrDefault("timestamp", "")))).append("</td></tr>");
            }
            html.append("</table></td></tr>");
        }

        // Pending reviews
        if (!pendingReviews.isEmpty()) {
            html.append("<tr><td style='padding:10px 30px 20px;'>").append(sectionTitle("🕐 Pending Review (&gt;24 hours)"))
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #fcd34d;border-radius:8px;overflow:hidden;font-size:13px;background:#fffbeb;'>")
                .append("<tr style='background:#fef3c7;'><th style='padding:10px 12px;text-align:left;color:#92400e;font-weight:600;border-bottom:1px solid #fcd34d;'>Candidate</th>")
                .append("<th style='padding:10px 12px;text-align:left;color:#92400e;font-weight:600;border-bottom:1px solid #fcd34d;'>Role</th>")
                .append("<th style='padding:10px 12px;text-align:center;color:#92400e;font-weight:600;border-bottom:1px solid #fcd34d;'>Waiting</th></tr>");
            for (Map<String, Object> p : pendingReviews) {
                long hours = ((Number) p.getOrDefault("hoursWaiting", 0)).longValue();
                html.append("<tr><td style='padding:9px 12px;color:#111827;border-bottom:1px solid #fef3c7;'>").append(esc(String.valueOf(p.getOrDefault("candidateName", "Unknown")))).append("</td>")
                    .append("<td style='padding:9px 12px;color:#374151;border-bottom:1px solid #fef3c7;'>").append(esc(String.valueOf(p.getOrDefault("jdTitle", "-")))).append("</td>")
                    .append("<td style='padding:9px 12px;text-align:center;color:#d97706;font-weight:600;border-bottom:1px solid #fef3c7;'>").append(hours).append("h</td></tr>");
            }
            html.append("</table></td></tr>");
        }

        // Pipeline
        if (pipelineStatus != null) {
            html.append("<tr><td style='padding:10px 30px 20px;'>").append(sectionTitle("👥 Candidate Pipeline"))
                .append("<table width='100%' cellpadding='0' cellspacing='0'><tr>")
                .append(pipelineCard("RFD", rfd, "Ready for Deployment", "#059669", "#d1fae5"))
                .append(pipelineCard("WFD", wfd, "Waiting for Deployment", "#2563eb", "#dbeafe"))
                .append(pipelineCard("DOB", dob, "Deploy Observe on Bill", "#7c3aed", "#ede9fe"))
                .append(pipelineCard("Deployed", deployed, "Currently Deployed", "#0891b2", "#cffafe"))
                .append("</tr></table>");
            if (todayRegistrations > 0)
                html.append("<p style='margin:10px 0 0;font-size:13px;color:#059669;font-weight:600;'>✅ ").append(todayRegistrations).append(" new candidate").append(todayRegistrations > 1 ? "s" : "").append(" registered today</p>");
            html.append("<p style='margin:6px 0 0;font-size:12px;color:#9ca3af;'>Total candidates: ").append(totalCandidates).append("</p></td></tr>");
        }

        // CTA
        html.append("<tr><td style='padding:20px 30px 30px;text-align:center;'>")
            .append("<a href='").append(dashboardUrl).append("' style='display:inline-block;background:linear-gradient(135deg,#1e3a5f 0%,#2d6a9f 100%);color:#ffffff;text-decoration:none;padding:14px 36px;border-radius:8px;font-weight:600;font-size:15px;'>View Full Dashboard →</a></td></tr>");

        // Footer
        html.append("<tr><td style='background:#f9fafb;padding:20px 30px;text-align:center;border-top:1px solid #e5e7eb;'>")
            .append("<p style='margin:0;color:#6b7280;font-size:12px;'>Bench Readiness Platform · Automated Daily Report · ").append(date).append("</p></td></tr>");

        html.append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    private String summaryCard(String label, String value, String color, String bg) {
        return "<td style='padding:0 6px 0 0;width:25%;'><div style='background:" + bg + ";border-radius:8px;padding:16px 12px;text-align:center;'>" +
            "<div style='font-size:26px;font-weight:700;color:" + color + ";'>" + value + "</div>" +
            "<div style='font-size:11px;color:#6b7280;margin-top:4px;font-weight:500;'>" + label + "</div></div></td>";
    }

    private String pipelineCard(String label, int count, String subtitle, String color, String bg) {
        return "<td style='padding:0 6px 0 0;width:25%;'><div style='background:" + bg + ";border-radius:8px;padding:14px 12px;text-align:center;'>" +
            "<div style='font-size:22px;font-weight:700;color:" + color + ";'>" + count + "</div>" +
            "<div style='font-size:12px;font-weight:600;color:" + color + ";'>" + label + "</div>" +
            "<div style='font-size:10px;color:#6b7280;margin-top:2px;'>" + subtitle + "</div></div></td>";
    }

    private String statusRow(String label, int count, String color, boolean altBg) {
        String bg = altBg ? "#f9fafb" : "#ffffff";
        return "<tr style='background:" + bg + ";'><td style='padding:10px 16px;color:#374151;font-size:13px;border-bottom:1px solid #f3f4f6;'>" + label + "</td>" +
            "<td style='padding:10px 16px;text-align:right;border-bottom:1px solid #f3f4f6;'>" +
            "<span style='background:" + color + ";color:#fff;font-size:12px;font-weight:700;padding:3px 10px;border-radius:12px;'>" + count + "</span></td></tr>";
    }

    private String sectionTitle(String title) {
        return "<p style='margin:0 0 10px;font-size:14px;font-weight:700;color:#1f2937;'>" + title + "</p>";
    }

    private String statusBadge(String status) {
        String color = switch (status) {
            case "SIGNED_OFF" -> "#059669"; case "REVIEW_PENDING" -> "#d97706";
            case "IN_PROGRESS" -> "#2563eb"; case "COMPLETED" -> "#7c3aed";
            case "SCHEDULED" -> "#6b7280"; default -> "#dc2626";
        };
        return "<span style='background:" + color + ";color:#fff;font-size:11px;font-weight:600;padding:2px 8px;border-radius:10px;white-space:nowrap;'>" + status.replace("_", " ") + "</span>";
    }

    private String verdictBadge(String verdict) {
        if (verdict == null || verdict.equals("Pending")) return "<span style='color:#9ca3af;font-size:12px;'>Pending</span>";
        String color = switch (verdict.replace("*", "")) {
            case "READY" -> "#059669"; case "NEEDS_1_WEEK_PREP" -> "#d97706";
            case "NEEDS_RESKILLING", "WITHDRAWN" -> "#dc2626"; case "MISMATCH_WITH_JD" -> "#7c3aed";
            default -> "#6b7280";
        };
        return "<span style='color:" + color + ";font-size:12px;font-weight:600;'>" + verdict.replace("_", " ") + "</span>";
    }

    private String modeBadge(String mode) {
        String color = switch (mode) {
            case "SCREENING" -> "#6b7280"; case "L1" -> "#2563eb"; case "L2" -> "#7c3aed";
            case "L3" -> "#d97706"; case "L4" -> "#dc2626"; default -> "#6b7280";
        };
        return "<span style='background:" + color + ";color:#fff;font-size:11px;font-weight:600;padding:2px 7px;border-radius:10px;'>" + mode + "</span>";
    }

    private String formatTimestamp(String ts) {
        if (ts == null || ts.isBlank() || ts.equals("null")) return "-";
        try { return ts.substring(0, 16).replace("T", " "); } catch (Exception e) { return ts; }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
