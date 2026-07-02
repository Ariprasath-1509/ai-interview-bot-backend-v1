package com.benchreadiness.ops.observer.service;

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

    @Value("${app.mail.from}")
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
        try {
            List<Map<String, Object>> recipients = authServiceClient.getAdmins();
            if (recipients.isEmpty()) {
                log.warn("No SUPER_ADMIN or ADMIN users found — skipping digest");
                return;
            }

            for (Map<String, Object> recipient : recipients) {
                String email = recipient.get("email") != null ? recipient.get("email").toString() : null;
                String role  = recipient.get("role") != null ? recipient.get("role").toString() : "";

                if (email == null || email.isBlank()) {
                    log.warn("Skipping admin with no email address");
                    continue;
                }
                // SUPER_ADMIN → full report (no branch filter)
                // ADMIN       → DEVELOPMENT branch only
                // TESTING_ADMIN → TESTING branch only
                String digestBranch = switch (role) {
                    case "SUPER_ADMIN"    -> null;
                    case "ADMIN"          -> "DEVELOPMENT";
                    case "TESTING_ADMIN"  -> "TESTING";
                    default -> {
                        log.debug("Skipping digest for unsupported role {} ({})", role, email);
                        yield "SKIP";
                    }
                };
                if ("SKIP".equals(digestBranch)) continue;

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
                    log.info("Daily report sent to: {} [role={}, branch={}]", email, role, digestBranch != null ? digestBranch : "ALL");
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

        Map<String, Object> metrics   = (Map<String, Object>) reportData.getOrDefault("interviewMetrics", Map.of());
        Map<String, Object> violations = (Map<String, Object>) reportData.getOrDefault("violations", Map.of());
        Map<String, Object> alerts    = (Map<String, Object>) reportData.getOrDefault("alerts", Map.of());
        Map<String, Object> verdictBreakdown = (Map<String, Object>) reportData.getOrDefault("verdictBreakdown", Map.of());
        List<Map<String, Object>> interviewList      = (List<Map<String, Object>>) reportData.getOrDefault("interviewList", List.of());
        List<Map<String, Object>> expiringInterviews = (List<Map<String, Object>>) reportData.getOrDefault("expiringInterviews", List.of());

        int totalToday    = ((Number) metrics.getOrDefault("totalToday", 0)).intValue();
        int totalThisWeek = ((Number) metrics.getOrDefault("totalThisWeek", 0)).intValue();
        int scheduled     = ((Number) metrics.getOrDefault("scheduled", 0)).intValue();
        int inProgress    = ((Number) metrics.getOrDefault("inProgress", 0)).intValue();
        int completed     = ((Number) metrics.getOrDefault("completed", 0)).intValue();
        int reviewPending = ((Number) metrics.getOrDefault("reviewPending", 0)).intValue();
        int signedOff     = ((Number) metrics.getOrDefault("signedOff", 0)).intValue();
        int readyCount    = ((Number) metrics.getOrDefault("readyCount", 0)).intValue();
        double successRate = ((Number) metrics.getOrDefault("successRate", 0)).doubleValue();

        int totalWithdrawn = ((Number) violations.getOrDefault("totalWithdrawn", 0)).intValue();
        List<Map<String, Object>> violationDetails = (List<Map<String, Object>>) violations.getOrDefault("details", List.of());

        int totalPendingCount  = ((Number) alerts.getOrDefault("pendingReviewCount", 0)).intValue();
        long warningPendingCount = ((Number) alerts.getOrDefault("warningPendingCount", 0)).longValue();
        List<Map<String, Object>> criticalPending = (List<Map<String, Object>>) alerts.getOrDefault("criticalPendingReviews", List.of());

        int rfd = 0, wfd = 0, dob = 0, deployed = 0, totalCandidates = 0, todayRegistrations = 0;
        if (pipelineStatus != null) {
            rfd               = ((Number) pipelineStatus.getOrDefault("rfd", 0)).intValue();
            wfd               = ((Number) pipelineStatus.getOrDefault("wfd", 0)).intValue();
            dob               = ((Number) pipelineStatus.getOrDefault("dob", 0)).intValue();
            deployed          = ((Number) pipelineStatus.getOrDefault("deployed", 0)).intValue();
            totalCandidates   = ((Number) pipelineStatus.getOrDefault("totalCandidates", 0)).intValue();
            todayRegistrations = ((Number) pipelineStatus.getOrDefault("todayRegistrations", 0)).intValue();
        }

        boolean hasCritical = !criticalPending.isEmpty();
        long vReady    = ((Number) verdictBreakdown.getOrDefault("READY", 0)).longValue();
        long vPrep     = ((Number) verdictBreakdown.getOrDefault("NEEDS_1_WEEK_PREP", 0)).longValue();
        long vReskill  = ((Number) verdictBreakdown.getOrDefault("NEEDS_RESKILLING", 0)).longValue();
        long vMismatch = ((Number) verdictBreakdown.getOrDefault("MISMATCH_WITH_JD", 0)).longValue();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
            .append("<title>Daily Platform Report</title></head>")
            .append("<body style='margin:0;padding:0;background:#f4f6f9;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,Arial,sans-serif;'>")
            .append("<table width='100%' cellpadding='0' cellspacing='0' style='background:#f4f6f9;padding:32px 16px;'><tr><td align='center'>")
            .append("<table width='620' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e2e8f0;'>");

        // ── Header bar ───────────────────────────────────────────────────────
        html.append("<tr><td style='background:#0f172a;padding:28px 32px;'>")
            .append("<table width='100%' cellpadding='0' cellspacing='0'><tr>")
            .append("<td><p style='margin:0;font-size:11px;font-weight:600;letter-spacing:2px;color:#64748b;text-transform:uppercase;'>Bench Readiness</p>")
            .append("<h1 style='margin:6px 0 0;color:#f8fafc;font-size:22px;font-weight:700;letter-spacing:-0.3px;'>Daily Platform Report</h1></td>")
            .append("<td style='text-align:right;vertical-align:top;'>")
            .append("<p style='margin:0;color:#94a3b8;font-size:13px;'>").append(date).append("</p>");
        if (branch != null)
            html.append("<span style='display:inline-block;margin-top:6px;background:#1e293b;color:#7dd3fc;font-size:11px;font-weight:600;padding:3px 10px;border-radius:20px;border:1px solid #334155;'>").append(esc(branch)).append("</span>");
        html.append("</td></tr></table></td></tr>");

        // ── Alert strip (only if there's something to flag) ──────────────────
        if (hasCritical || totalWithdrawn > 0 || !expiringInterviews.isEmpty()) {
            html.append("<tr><td style='background:#fef2f2;border-bottom:1px solid #fecaca;padding:12px 32px;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0'><tr>");
            if (hasCritical)
                html.append("<td style='font-size:12px;font-weight:600;color:#b91c1c;padding-right:20px;white-space:nowrap;'>&#9679; ").append(criticalPending.size()).append(" critical review").append(criticalPending.size() > 1 ? "s" : "").append(" &gt;72h</td>");
            if (totalWithdrawn > 0)
                html.append("<td style='font-size:12px;font-weight:600;color:#b91c1c;padding-right:20px;white-space:nowrap;'>&#9679; ").append(totalWithdrawn).append(" violation").append(totalWithdrawn > 1 ? "s" : "").append(" today</td>");
            if (!expiringInterviews.isEmpty())
                html.append("<td style='font-size:12px;font-weight:600;color:#b45309;white-space:nowrap;'>&#9679; ").append(expiringInterviews.size()).append(" expiring in 48h</td>");
            html.append("<td style='text-align:right;'><a href='").append(dashboardUrl).append("' style='font-size:12px;color:#dc2626;font-weight:600;text-decoration:none;'>Action required &rarr;</a></td>")
                .append("</tr></table></td></tr>");
        }

        // ── 4 summary metric cards ────────────────────────────────────────────
        html.append("<tr><td style='padding:28px 32px 20px;'>")
            .append("<p style='margin:0 0 14px;font-size:11px;font-weight:600;letter-spacing:1.5px;color:#94a3b8;text-transform:uppercase;'>At a glance</p>")
            .append("<table width='100%' cellpadding='0' cellspacing='0'><tr>")
            .append(metricCard("Interviews today", String.valueOf(totalToday), "#1d4ed8", "#eff6ff", "#bfdbfe"))
            .append(metricCard("This week", String.valueOf(totalThisWeek), "#7c3aed", "#f5f3ff", "#ddd6fe"))
            .append(metricCard("Ready (total)", String.valueOf(readyCount), "#065f46", "#f0fdf4", "#bbf7d0"))
            .append(metricCard("Success rate", String.format("%.0f%%", successRate), successRate >= 70 ? "#065f46" : "#92400e", successRate >= 70 ? "#f0fdf4" : "#fffbeb", successRate >= 70 ? "#bbf7d0" : "#fde68a"))
            .append("</tr></table></td></tr>");

        // ── Divider ──────────────────────────────────────────────────────────
        html.append(divider());

        // ── Pipeline status ───────────────────────────────────────────────────
        html.append("<tr><td style='padding:20px 32px;'>")
            .append(sectionHeader("Current pipeline status", "Reflects live state of all interviews"))
            .append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-top:12px;border-radius:10px;overflow:hidden;border:1px solid #e2e8f0;'>")
            .append(pipelineRow("Scheduled",                scheduled,     "#2563eb", "#eff6ff", true))
            .append(pipelineRow("In progress",              inProgress,    "#d97706", "#fffbeb", false))
            .append(pipelineRow("Completed — awaiting sign-off", completed, "#7c3aed", "#f5f3ff", true))
            .append(pipelineRow("Review pending",           reviewPending, "#dc2626", "#fef2f2", false))
            .append(pipelineRow("Signed off",               signedOff,     "#059669", "#f0fdf4", true))
            .append(pipelineRow("Withdrawn / violations today", totalWithdrawn, "#9f1239", "#fff1f2", false))
            .append("</table></td></tr>");

        // ── Verdict breakdown ─────────────────────────────────────────────────
        if (vReady + vPrep + vReskill + vMismatch > 0) {
            html.append(divider());
            html.append("<tr><td style='padding:20px 32px;'>")
                .append(sectionHeader("Verdict breakdown", "All signed-off interviews"))
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-top:12px;'>")
                .append("<tr>")
                .append(verdictCard("Ready", vReady, "#065f46", "#f0fdf4", "#bbf7d0"))
                .append(verdictCard("Needs 1-week prep", vPrep, "#92400e", "#fffbeb", "#fde68a"))
                .append(verdictCard("Needs reskilling", vReskill, "#9f1239", "#fff1f2", "#fecdd3"))
                .append(verdictCard("Mismatch with JD", vMismatch, "#4c1d95", "#f5f3ff", "#ddd6fe"))
                .append("</tr></table></td></tr>");
        }

        // ── Today's interviews ────────────────────────────────────────────────
        if (!interviewList.isEmpty()) {
            html.append(divider());
            html.append("<tr><td style='padding:20px 32px;'>")
                .append(sectionHeader("Today's interviews", interviewList.size() + " scheduled"))
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-top:12px;font-size:13px;border:1px solid #e2e8f0;border-radius:10px;overflow:hidden;'>")
                .append("<tr style='background:#f8fafc;'>")
                .append("<th style='padding:10px 14px;text-align:left;color:#475569;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #e2e8f0;'>Candidate</th>")
                .append("<th style='padding:10px 14px;text-align:left;color:#475569;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #e2e8f0;'>Role</th>")
                .append("<th style='padding:10px 14px;text-align:center;color:#475569;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #e2e8f0;'>Mode</th>")
                .append("<th style='padding:10px 14px;text-align:center;color:#475569;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #e2e8f0;'>Status</th>")
                .append("<th style='padding:10px 14px;text-align:center;color:#475569;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #e2e8f0;'>Verdict</th></tr>");
            for (int i = 0; i < interviewList.size(); i++) {
                Map<String, Object> interview = interviewList.get(i);
                String bg = i % 2 == 0 ? "#ffffff" : "#f8fafc";
                String status = String.valueOf(interview.getOrDefault("status", "-"));
                String verdict = interview.get("finalVerdict") != null ? String.valueOf(interview.get("finalVerdict"))
                    : interview.get("proposedVerdict") != null ? String.valueOf(interview.get("proposedVerdict")) + "*" : "Pending";
                html.append("<tr style='background:").append(bg).append(";'>")
                    .append("<td style='padding:10px 14px;color:#0f172a;font-weight:500;border-bottom:1px solid #f1f5f9;'>").append(esc(String.valueOf(interview.getOrDefault("candidateName", "Unknown")))).append("</td>")
                    .append("<td style='padding:10px 14px;color:#475569;font-size:12px;border-bottom:1px solid #f1f5f9;'>").append(esc(String.valueOf(interview.getOrDefault("jdTitle", "-")))).append("</td>")
                    .append("<td style='padding:10px 14px;text-align:center;border-bottom:1px solid #f1f5f9;'>").append(modeBadge(String.valueOf(interview.getOrDefault("mode", "-")))).append("</td>")
                    .append("<td style='padding:10px 14px;text-align:center;border-bottom:1px solid #f1f5f9;'>").append(statusBadge(status)).append("</td>")
                    .append("<td style='padding:10px 14px;text-align:center;border-bottom:1px solid #f1f5f9;'>").append(verdictBadge(verdict)).append("</td></tr>");
            }
            html.append("</table>")
                .append("<p style='margin:8px 0 0;font-size:11px;color:#94a3b8;'>* proposed verdict — pending admin sign-off</p>")
                .append("</td></tr>");
        }

        // ── Violations ────────────────────────────────────────────────────────
        if (!violationDetails.isEmpty()) {
            html.append(divider());
            html.append("<tr><td style='padding:20px 32px;'>")
                .append(sectionHeader("Integrity violations today", totalWithdrawn + " flagged"))
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-top:12px;font-size:13px;border:1px solid #fecaca;border-radius:10px;overflow:hidden;background:#fff5f5;'>")
                .append("<tr style='background:#fee2e2;'>")
                .append("<th style='padding:10px 14px;text-align:left;color:#9f1239;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #fecaca;'>Candidate</th>")
                .append("<th style='padding:10px 14px;text-align:left;color:#9f1239;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #fecaca;'>Reason</th>")
                .append("<th style='padding:10px 14px;text-align:left;color:#9f1239;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #fecaca;'>Time</th></tr>");
            for (Map<String, Object> v : violationDetails) {
                html.append("<tr><td style='padding:10px 14px;color:#0f172a;font-weight:500;border-bottom:1px solid #fee2e2;'>").append(esc(String.valueOf(v.getOrDefault("candidateName", "Unknown")))).append("</td>")
                    .append("<td style='padding:10px 14px;border-bottom:1px solid #fee2e2;'>")
                    .append("<span style='background:#fee2e2;color:#b91c1c;font-size:11px;font-weight:600;padding:2px 8px;border-radius:20px;'>").append(esc(String.valueOf(v.getOrDefault("reason", "Unknown")))).append("</span></td>")
                    .append("<td style='padding:10px 14px;color:#64748b;font-size:12px;border-bottom:1px solid #fee2e2;'>").append(formatTimestamp(String.valueOf(v.getOrDefault("timestamp", "")))).append("</td></tr>");
            }
            html.append("</table></td></tr>");
        }

        // ── Critical pending reviews ──────────────────────────────────────────
        if (!criticalPending.isEmpty()) {
            html.append(divider());
            html.append("<tr><td style='padding:20px 32px;'>")
                .append(sectionHeader("Overdue reviews — action required", criticalPending.size() + " critical (&gt;72h), showing oldest first"))
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-top:12px;font-size:13px;border:1px solid #fecaca;border-radius:10px;overflow:hidden;'>")
                .append("<tr style='background:#fff1f2;'>")
                .append("<th style='padding:10px 14px;text-align:left;color:#9f1239;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #fecaca;'>Candidate</th>")
                .append("<th style='padding:10px 14px;text-align:left;color:#9f1239;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #fecaca;'>Role</th>")
                .append("<th style='padding:10px 14px;text-align:right;color:#9f1239;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #fecaca;'>Waiting</th></tr>");
            for (int i = 0; i < criticalPending.size(); i++) {
                Map<String, Object> p = criticalPending.get(i);
                long hours = ((Number) p.getOrDefault("hoursWaiting", 0)).longValue();
                long days  = hours / 24;
                String waitLabel = days > 0 ? days + "d " + (hours % 24) + "h" : hours + "h";
                String bg = i % 2 == 0 ? "#ffffff" : "#fff5f5";
                html.append("<tr style='background:").append(bg).append(";'>")
                    .append("<td style='padding:10px 14px;color:#0f172a;font-weight:500;border-bottom:1px solid #fff1f2;'>").append(esc(String.valueOf(p.getOrDefault("candidateName", "Unknown")))).append("</td>")
                    .append("<td style='padding:10px 14px;color:#475569;font-size:12px;border-bottom:1px solid #fff1f2;'>").append(esc(String.valueOf(p.getOrDefault("jdTitle", "-")))).append("</td>")
                    .append("<td style='padding:10px 14px;text-align:right;border-bottom:1px solid #fff1f2;'>")
                    .append("<span style='background:#fee2e2;color:#b91c1c;font-size:12px;font-weight:700;padding:3px 10px;border-radius:20px;'>").append(waitLabel).append("</span></td></tr>");
            }
            html.append("</table>");
            if (warningPendingCount > 0)
                html.append("<p style='margin:10px 0 0;font-size:12px;color:#d97706;'>+ ").append(warningPendingCount).append(" more in the 24–72h range. <a href='").append(dashboardUrl).append("' style='color:#d97706;font-weight:600;'>Open dashboard &rarr;</a></p>");
            html.append("</td></tr>");
        } else if (totalPendingCount > 0) {
            html.append(divider());
            html.append("<tr><td style='padding:20px 32px;'>")
                .append("<div style='background:#fffbeb;border:1px solid #fde68a;border-radius:10px;padding:14px 18px;font-size:13px;color:#92400e;'>")
                .append("<strong>").append(totalPendingCount).append(" interviews</strong> have been pending review for more than 24 hours. ")
                .append("<a href='").append(dashboardUrl).append("' style='color:#d97706;font-weight:600;'>Review in dashboard &rarr;</a>")
                .append("</div></td></tr>");
        }

        // ── Expiring soon ─────────────────────────────────────────────────────
        if (!expiringInterviews.isEmpty()) {
            html.append(divider());
            html.append("<tr><td style='padding:20px 32px;'>")
                .append(sectionHeader("Expiring in 48 hours", expiringInterviews.size() + " interview" + (expiringInterviews.size() > 1 ? "s" : "")))
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-top:12px;font-size:13px;border:1px solid #fed7aa;border-radius:10px;overflow:hidden;'>")
                .append("<tr style='background:#fff7ed;'>")
                .append("<th style='padding:10px 14px;text-align:left;color:#9a3412;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #fed7aa;'>Candidate</th>")
                .append("<th style='padding:10px 14px;text-align:center;color:#9a3412;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #fed7aa;'>Mode</th>")
                .append("<th style='padding:10px 14px;text-align:right;color:#9a3412;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;border-bottom:1px solid #fed7aa;'>Expires</th></tr>");
            for (int i = 0; i < expiringInterviews.size(); i++) {
                Map<String, Object> e = expiringInterviews.get(i);
                String bg = i % 2 == 0 ? "#ffffff" : "#fff7ed";
                html.append("<tr style='background:").append(bg).append(";'>")
                    .append("<td style='padding:10px 14px;color:#0f172a;font-weight:500;border-bottom:1px solid #ffedd5;'>").append(esc(String.valueOf(e.getOrDefault("candidateName", "Unknown")))).append("</td>")
                    .append("<td style='padding:10px 14px;text-align:center;border-bottom:1px solid #ffedd5;'>").append(modeBadge(String.valueOf(e.getOrDefault("mode", "-")))).append("</td>")
                    .append("<td style='padding:10px 14px;text-align:right;color:#ea580c;font-weight:600;border-bottom:1px solid #ffedd5;font-size:12px;'>").append(formatTimestamp(String.valueOf(e.getOrDefault("expiresAt", "")))).append("</td></tr>");
            }
            html.append("</table></td></tr>");
        }

        // ── Candidate pipeline cards ──────────────────────────────────────────
        if (pipelineStatus != null) {
            html.append(divider());
            html.append("<tr><td style='padding:20px 32px;'>")
                .append(sectionHeader("Candidate pipeline", "Total: " + totalCandidates))
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-top:12px;'><tr>")
                .append(candidatePipelineCard("RFD", rfd, "Ready for deployment", "#065f46", "#f0fdf4", "#bbf7d0"))
                .append(candidatePipelineCard("WFD", wfd, "Waiting for deployment", "#1e40af", "#eff6ff", "#bfdbfe"))
                .append(candidatePipelineCard("DOB", dob, "Deploy observe on bill", "#5b21b6", "#f5f3ff", "#ddd6fe"))
                .append(candidatePipelineCard("Deployed", deployed, "Currently deployed", "#0e7490", "#ecfeff", "#a5f3fc"))
                .append("</tr></table>");
            if (todayRegistrations > 0)
                html.append("<p style='margin:12px 0 0;font-size:13px;color:#059669;font-weight:500;'>").append(todayRegistrations).append(" new candidate").append(todayRegistrations > 1 ? "s" : "").append(" registered today</p>");
            html.append("</td></tr>");
        }

        // ── CTA ───────────────────────────────────────────────────────────────
        html.append(divider());
        html.append("<tr><td style='padding:24px 32px 32px;text-align:center;'>")
            .append("<a href='").append(dashboardUrl).append("' style='display:inline-block;background:#0f172a;color:#f8fafc;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:14px;letter-spacing:0.2px;'>Open dashboard &rarr;</a></td></tr>");

        // ── Footer ────────────────────────────────────────────────────────────
        html.append("<tr><td style='background:#f8fafc;padding:18px 32px;text-align:center;border-top:1px solid #e2e8f0;'>")
            .append("<p style='margin:0;color:#94a3b8;font-size:12px;'>Bench Readiness &middot; Automated daily report &middot; ").append(date).append("</p></td></tr>");

        html.append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    private String divider() {
        return "<tr><td style='padding:0 32px;'><div style='border-top:1px solid #e2e8f0;'></div></td></tr>";
    }

    private String sectionHeader(String title, String subtitle) {
        return "<table width='100%' cellpadding='0' cellspacing='0'><tr>"
            + "<td><p style='margin:0;font-size:14px;font-weight:600;color:#0f172a;'>" + title + "</p></td>"
            + "<td style='text-align:right;'><span style='font-size:12px;color:#94a3b8;'>" + subtitle + "</span></td>"
            + "</tr></table>";
    }

    private String metricCard(String label, String value, String textColor, String bg, String border) {
        return "<td style='padding:0 6px 0 0;width:25%;'>"
            + "<div style='background:" + bg + ";border:1px solid " + border + ";border-radius:10px;padding:14px 12px;text-align:center;'>"
            + "<div style='font-size:28px;font-weight:700;color:" + textColor + ";letter-spacing:-1px;'>" + value + "</div>"
            + "<div style='font-size:11px;color:" + textColor + ";margin-top:4px;font-weight:500;opacity:0.75;'>" + label + "</div>"
            + "</div></td>";
    }

    private String pipelineRow(String label, int count, String badgeColor, String badgeBg, boolean altBg) {
        String bg = altBg ? "#ffffff" : "#fafafa";
        return "<tr style='background:" + bg + ";'>"
            + "<td style='padding:11px 16px;color:#374151;font-size:13px;border-bottom:1px solid #f1f5f9;'>" + label + "</td>"
            + "<td style='padding:11px 16px;text-align:right;border-bottom:1px solid #f1f5f9;'>"
            + "<span style='background:" + badgeBg + ";color:" + badgeColor + ";font-size:13px;font-weight:700;padding:3px 12px;border-radius:20px;'>" + count + "</span></td></tr>";
    }

    private String verdictCard(String label, long count, String textColor, String bg, String border) {
        return "<td style='padding:0 6px 0 0;width:25%;'>"
            + "<div style='background:" + bg + ";border:1px solid " + border + ";border-radius:10px;padding:12px 10px;text-align:center;'>"
            + "<div style='font-size:22px;font-weight:700;color:" + textColor + ";'>" + count + "</div>"
            + "<div style='font-size:10px;color:" + textColor + ";margin-top:3px;font-weight:500;opacity:0.75;line-height:1.3;'>" + label + "</div>"
            + "</div></td>";
    }

    private String candidatePipelineCard(String code, int count, String subtitle, String textColor, String bg, String border) {
        return "<td style='padding:0 6px 0 0;width:25%;'>"
            + "<div style='background:" + bg + ";border:1px solid " + border + ";border-radius:10px;padding:14px 10px;text-align:center;'>"
            + "<div style='font-size:24px;font-weight:700;color:" + textColor + ";'>" + count + "</div>"
            + "<div style='font-size:12px;font-weight:700;color:" + textColor + ";margin-top:2px;'>" + code + "</div>"
            + "<div style='font-size:10px;color:" + textColor + ";margin-top:2px;opacity:0.65;line-height:1.3;'>" + subtitle + "</div>"
            + "</div></td>";
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
