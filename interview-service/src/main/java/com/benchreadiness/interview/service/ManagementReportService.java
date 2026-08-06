package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.ComplianceServiceClient;
import com.benchreadiness.interview.entity.*;
import com.benchreadiness.interview.repository.InterviewRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ManagementReportService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ManagementReportService.class);

    // Fonts
    private static final Font BRAND_FONT     = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(255, 255, 255));
    private static final Font TITLE_FONT     = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(33, 47, 61));
    private static final Font SECTION_FONT   = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(33, 47, 61));
    private static final Font LABEL_FONT     = new Font(Font.HELVETICA, 9,  Font.BOLD, new Color(100, 100, 100));
    private static final Font VALUE_FONT     = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(33, 47, 61));
    private static final Font VALUE_BOLD     = new Font(Font.HELVETICA, 10, Font.BOLD,   new Color(33, 47, 61));
    private static final Font SMALL_FONT     = new Font(Font.HELVETICA, 8,  Font.NORMAL, new Color(120, 120, 120));
    private static final Font TABLE_HDR_FONT = new Font(Font.HELVETICA, 9,  Font.BOLD,   new Color(255, 255, 255));
    private static final Font TABLE_VAL_FONT = new Font(Font.HELVETICA, 9,  Font.NORMAL, new Color(33, 47, 61));

    // Palette
    private static final Color NAVY       = new Color(26, 82, 118);
    private static final Color NAVY_LIGHT = new Color(40, 116, 166);
    private static final Color ROW_ALT    = new Color(244, 246, 247);
    private static final Color ROW_WHITE  = Color.WHITE;
    private static final Color GREEN      = new Color(39, 174, 96);
    private static final Color AMBER      = new Color(230, 126, 34);
    private static final Color RED        = new Color(231, 76, 60);
    private static final Color BLUE_LIGHT = new Color(133, 193, 233);

    private final InterviewRepository interviewRepository;
    private final ComplianceServiceClient complianceServiceClient;

    public ManagementReportService(InterviewRepository interviewRepository,
                                   ComplianceServiceClient complianceServiceClient) {
        this.interviewRepository = interviewRepository;
        this.complianceServiceClient = complianceServiceClient;
    }

    public byte[] generateReport(LocalDate from, LocalDate to) throws Exception {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // All interviews strictly within the requested date range
        List<Interview> all = interviewRepository.findAll().stream()
                .filter(i -> !i.getCreatedAt().isBefore(fromInstant) && i.getCreatedAt().isBefore(toInstant))
                .collect(Collectors.toList());

        // Token analytics scoped to the same date range (best-effort)
        String fromStr = from.toString();
        String toStr   = to.toString();
        Map<String, Object> tokenRange    = safeCall(() -> complianceServiceClient.getTokenAnalyticsForRange(fromStr, toStr));
        Map<String, Object> perInterview  = safeCall(() -> complianceServiceClient.getPerInterviewTokenAnalytics());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 50);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new FooterPageEvent(from, to));
        doc.open();

        addCoverHeader(doc, from, to);
        addExecutiveSummary(doc, all, from, to);
        addPipelineSection(doc, all);
        addVerdictSection(doc, all);
        addModeSection(doc, all);
        addActivityTrend(doc, all, from, to);
        addTokenSection(doc, tokenRange, perInterview);

        doc.close();
        return baos.toByteArray();
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private void addCoverHeader(Document doc, LocalDate from, LocalDate to) throws DocumentException {
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(20);

        PdfPCell titleCell = new PdfPCell(new Phrase("BenchReadiness Platform", BRAND_FONT));
        titleCell.setBackgroundColor(NAVY);
        titleCell.setPadding(16);
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        banner.addCell(titleCell);

        PdfPCell subCell = new PdfPCell(new Phrase(
                "Management Report  |  " + fmt(from) + "  –  " + fmt(to),
                new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(200, 220, 240))));
        subCell.setBackgroundColor(NAVY_LIGHT);
        subCell.setPadding(8);
        subCell.setBorder(Rectangle.NO_BORDER);
        banner.addCell(subCell);

        doc.add(banner);
    }

    private void addExecutiveSummary(Document doc, List<Interview> all,
                                      LocalDate from, LocalDate to) throws DocumentException {
        sectionHeading(doc, "Executive Summary");

        long total      = all.size();
        long scheduled  = count(all, InterviewStatus.SCHEDULED);
        long attended   = count(all, InterviewStatus.COMPLETED) + count(all, InterviewStatus.SIGNED_OFF);
        long inProgress = count(all, InterviewStatus.IN_PROGRESS);
        long reviewPending = count(all, InterviewStatus.REVIEW_PENDING);
        long signedOff  = count(all, InterviewStatus.SIGNED_OFF);
        long withdrawn  = all.stream()
                .filter(i -> i.getProposedVerdict() == ReadinessVerdict.WITHDRAWN
                        || i.getFinalVerdict() == ReadinessVerdict.WITHDRAWN)
                .count();

        double attendanceRate = total > 0 ? (double) attended / total * 100 : 0;
        long ready = verdictCount(all, ReadinessVerdict.READY);
        long totalAssessed = all.stream().filter(i -> i.getFinalVerdict() != null).count();
        double successRate = totalAssessed > 0 ? (double) ready / totalAssessed * 100 : 0;

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingAfter(14);
        t.setWidths(new float[]{1, 1, 1, 1});

        addKpiCell(t, "Total Interviews", String.valueOf(total), NAVY);
        addKpiCell(t, "Attended (Completed)", String.valueOf(attended), GREEN);
        addKpiCell(t, "Attendance Rate", pct(attendanceRate), attendanceRate >= 70 ? GREEN : AMBER);
        addKpiCell(t, "Ready Rate", pct(successRate), successRate >= 50 ? GREEN : AMBER);

        doc.add(t);

        PdfPTable t2 = new PdfPTable(4);
        t2.setWidthPercentage(100);
        t2.setSpacingAfter(18);
        t2.setWidths(new float[]{1, 1, 1, 1});

        addKpiCell(t2, "Scheduled",     String.valueOf(scheduled),    NAVY_LIGHT);
        addKpiCell(t2, "In Progress",   String.valueOf(inProgress),   NAVY_LIGHT);
        addKpiCell(t2, "Pending Review",String.valueOf(reviewPending), NAVY_LIGHT);
        addKpiCell(t2, "Withdrawn",     String.valueOf(withdrawn),     withdrawn > 0 ? RED : NAVY_LIGHT);

        doc.add(t2);
    }

    private void addPipelineSection(Document doc, List<Interview> all) throws DocumentException {
        sectionHeading(doc, "Interview Pipeline — Status Breakdown");

        String[][] rows = {
            { "Draft",          String.valueOf(count(all, InterviewStatus.DRAFT)) },
            { "Scheduled",      String.valueOf(count(all, InterviewStatus.SCHEDULED)) },
            { "In Progress",    String.valueOf(count(all, InterviewStatus.IN_PROGRESS)) },
            { "Completed",      String.valueOf(count(all, InterviewStatus.COMPLETED)) },
            { "Review Pending", String.valueOf(count(all, InterviewStatus.REVIEW_PENDING)) },
            { "Signed Off",     String.valueOf(count(all, InterviewStatus.SIGNED_OFF)) },
            { "Withdrawn",      String.valueOf(count(all, InterviewStatus.WITHDRAWN)) },
            { "Expired",        String.valueOf(count(all, InterviewStatus.EXPIRED)) },
        };

        doc.add(buildTwoColTable(new String[]{"Status", "Count"}, rows));
    }

    private void addVerdictSection(Document doc, List<Interview> all) throws DocumentException {
        sectionHeading(doc, "Assessment Outcomes (Final Verdicts)");

        long totalAssessed = all.stream().filter(i -> i.getFinalVerdict() != null).count();

        ReadinessVerdict[] verdicts = {
            ReadinessVerdict.READY, ReadinessVerdict.NEEDS_1_WEEK_PREP,
            ReadinessVerdict.NEEDS_RESKILLING, ReadinessVerdict.MISMATCH_WITH_JD,
            ReadinessVerdict.WITHDRAWN
        };

        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setSpacingAfter(14);
        addHdrCells(t, "Verdict", "Count", "% of Assessed");

        boolean alt = false;
        for (ReadinessVerdict v : verdicts) {
            long c = verdictCount(all, v);
            double pctVal = totalAssessed > 0 ? (double) c / totalAssessed * 100 : 0;
            Color bg = alt ? ROW_ALT : ROW_WHITE;
            addBodyCell(t, v.name().replace("_", " "), bg);
            addBodyCellRight(t, String.valueOf(c), bg);
            addBodyCellRight(t, pct(pctVal), bg);
            alt = !alt;
        }

        if (totalAssessed == 0) {
            PdfPCell empty = new PdfPCell(new Phrase("No assessed interviews in this period.", SMALL_FONT));
            empty.setColspan(3);
            empty.setBorder(Rectangle.NO_BORDER);
            empty.setPadding(8);
            t.addCell(empty);
        }

        doc.add(t);
    }

    private void addModeSection(Document doc, List<Interview> all) throws DocumentException {
        sectionHeading(doc, "Interview Mode Distribution");

        Map<String, Long> modes = all.stream()
                .collect(Collectors.groupingBy(
                        (Interview i) -> i.getInterviewMode() != null ? i.getInterviewMode().name() : "UNKNOWN",
                        Collectors.counting()));

        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setSpacingAfter(14);
        addHdrCells(t, "Mode", "Count", "% of Total");

        boolean alt = false;
        for (Map.Entry<String, Long> e : modes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toList())) {
            double pctVal = all.size() > 0 ? (double) e.getValue() / all.size() * 100 : 0;
            Color bg = alt ? ROW_ALT : ROW_WHITE;
            addBodyCell(t, e.getKey(), bg);
            addBodyCellRight(t, String.valueOf(e.getValue()), bg);
            addBodyCellRight(t, pct(pctVal), bg);
            alt = !alt;
        }

        if (modes.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No data for this period.", SMALL_FONT));
            empty.setColspan(3); empty.setBorder(Rectangle.NO_BORDER); empty.setPadding(8);
            t.addCell(empty);
        }

        doc.add(t);
    }

    private void addActivityTrend(Document doc, List<Interview> all,
                                   LocalDate from, LocalDate to) throws DocumentException {
        long rangeDays = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        boolean useWeekly = rangeDays > 60;
        String heading = useWeekly
                ? "Weekly Activity Trend (" + fmt(from) + " – " + fmt(to) + ")"
                : "Daily Activity (" + fmt(from) + " – " + fmt(to) + ")";
        sectionHeading(doc, heading);

        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        t.setSpacingAfter(14);
        t.setWidths(new float[]{2.5f, 1, 1, 1, 1});
        addHdrCells(t, useWeekly ? "Week" : "Date", "Created", "Completed", "Ready", "Success %");

        boolean alt = false;
        if (useWeekly) {
            // Iterate week by week
            LocalDate weekStart = from;
            while (!weekStart.isAfter(to)) {
                LocalDate weekEnd = weekStart.plusDays(6).isAfter(to) ? to : weekStart.plusDays(6);
                Instant s = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant e = weekEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                long created   = all.stream().filter(x -> inRange(x.getCreatedAt(), s, e)).count();
                long completed = all.stream().filter(x -> inRange(x.getCreatedAt(), s, e))
                        .filter(x -> x.getStatus() == InterviewStatus.COMPLETED || x.getStatus() == InterviewStatus.SIGNED_OFF).count();
                long ready     = all.stream().filter(x -> inRange(x.getCreatedAt(), s, e))
                        .filter(x -> x.getFinalVerdict() == ReadinessVerdict.READY).count();
                double sr = completed > 0 ? (double) ready / completed * 100 : 0;
                Color bg = alt ? ROW_ALT : ROW_WHITE;
                addBodyCell(t, fmt(weekStart) + " – " + fmt(weekEnd), bg);
                addBodyCellRight(t, String.valueOf(created), bg);
                addBodyCellRight(t, String.valueOf(completed), bg);
                addBodyCellRight(t, String.valueOf(ready), bg);
                addBodyCellRight(t, completed > 0 ? pct(sr) : "—", bg);
                alt = !alt;
                weekStart = weekStart.plusDays(7);
            }
        } else {
            LocalDate cursor = from;
            while (!cursor.isAfter(to)) {
                Instant s = cursor.atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant e = cursor.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                long created   = all.stream().filter(x -> inRange(x.getCreatedAt(), s, e)).count();
                long completed = all.stream().filter(x -> inRange(x.getCreatedAt(), s, e))
                        .filter(x -> x.getStatus() == InterviewStatus.COMPLETED || x.getStatus() == InterviewStatus.SIGNED_OFF).count();
                long ready     = all.stream().filter(x -> inRange(x.getCreatedAt(), s, e))
                        .filter(x -> x.getFinalVerdict() == ReadinessVerdict.READY).count();
                double sr = completed > 0 ? (double) ready / completed * 100 : 0;
                Color bg = alt ? ROW_ALT : ROW_WHITE;
                addBodyCell(t, cursor.format(DateTimeFormatter.ofPattern("EEE, MMM d")), bg);
                addBodyCellRight(t, String.valueOf(created), bg);
                addBodyCellRight(t, String.valueOf(completed), bg);
                addBodyCellRight(t, String.valueOf(ready), bg);
                addBodyCellRight(t, completed > 0 ? pct(sr) : "—", bg);
                alt = !alt;
                cursor = cursor.plusDays(1);
            }
        }

        doc.add(t);
    }

    private void addTokenSection(Document doc,
                                  Map<String, Object> tokenRange,
                                  Map<String, Object> perInterview) throws DocumentException {
        sectionHeading(doc, "AI Token Usage & Cost — Selected Period");

        long rangeTotal = longVal(tokenRange, "totalTokens");
        long avgDaily   = longVal(tokenRange, "avgDailyTokens");
        long avgPI      = longVal(perInterview, "avgTotalTokens");

        PdfPTable kpi = new PdfPTable(3);
        kpi.setWidthPercentage(100);
        kpi.setSpacingAfter(14);
        addKpiCell(kpi, "Total Tokens (Period)",   formatNumber(rangeTotal), NAVY);
        addKpiCell(kpi, "Avg Tokens / Day",         formatNumber(avgDaily),   NAVY_LIGHT);
        addKpiCell(kpi, "Avg Tokens / Interview",   formatNumber(avgPI),      NAVY_LIGHT);
        doc.add(kpi);

        PdfPTable kpi2 = new PdfPTable(3);
        kpi2.setWidthPercentage(100);
        kpi2.setSpacingAfter(14);
        addKpiCell(kpi2, "Avg Assessment Tokens", formatNumber(longVal(perInterview, "avgAssessmentTokens")), NAVY_LIGHT);
        addKpiCell(kpi2, "Avg Question Tokens",   formatNumber(longVal(perInterview, "avgQuestionTokens")),   NAVY_LIGHT);
        addKpiCell(kpi2, "Avg Rubric Tokens",     formatNumber(longVal(perInterview, "avgRubricTokens")),     NAVY_LIGHT);
        doc.add(kpi2);

        // Daily token buckets for the selected period
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> buckets = (java.util.List<Map<String, Object>>)
                (tokenRange != null ? tokenRange.getOrDefault("buckets", java.util.List.of()) : java.util.List.of());
        if (!buckets.isEmpty()) {
            Paragraph sub = new Paragraph("Daily Token Usage for Selected Period", SECTION_FONT);
            sub.setSpacingBefore(6);
            sub.setSpacingAfter(6);
            doc.add(sub);

            PdfPTable t = new PdfPTable(3);
            t.setWidthPercentage(100);
            t.setSpacingAfter(14);
            t.setWidths(new float[]{2, 1.5f, 1.5f});
            addHdrCells(t, "Date", "Tokens Used", "Est. Cost (USD)");

            boolean alt = false;
            for (Map<String, Object> b : buckets) {
                Color bg = alt ? ROW_ALT : ROW_WHITE;
                addBodyCell(t, str(b.get("label")), bg);
                addBodyCellRight(t, formatNumber(longVal(b, "tokens")), bg);
                Object costRaw = b.get("costUsd");
                String costStr = costRaw != null ? "$" + new BigDecimal(costRaw.toString())
                        .setScale(4, java.math.RoundingMode.HALF_UP).toPlainString() : "$0.0000";
                addBodyCellRight(t, costStr, bg);
                alt = !alt;
            }
            doc.add(t);
        }

        // Cost summary
        PdfPTable costSummary = new PdfPTable(2);
        costSummary.setWidthPercentage(60);
        costSummary.setHorizontalAlignment(Element.ALIGN_LEFT);
        costSummary.setSpacingAfter(14);
        addInfoRow2(costSummary, "Period Cost (USD):", formatCost(tokenRange != null ? tokenRange.get("totalCostUsd") : null));
        addInfoRow2(costSummary, "Avg Cost / Interview (all-time):", formatCost(perInterview != null ? perInterview.get("avgCostPerInterviewUsd") : null));
        addInfoRow2(costSummary, "Total AI Cost (all-time USD):", formatCost(perInterview != null ? perInterview.get("totalCostUsd") : null));
        doc.add(costSummary);
    }

    // ── PDF helpers ───────────────────────────────────────────────────────────

    private void sectionHeading(Document doc, String title) throws DocumentException {
        doc.add(new LineSeparator(1f, 100f, NAVY, Element.ALIGN_LEFT, -2f));
        Paragraph p = new Paragraph(title.toUpperCase(), SECTION_FONT);
        p.setSpacingBefore(10);
        p.setSpacingAfter(8);
        doc.add(p);
    }

    private void addKpiCell(PdfPTable t, String label, String value, Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setPadding(12);
        cell.setBorder(Rectangle.NO_BORDER);

        Paragraph content = new Paragraph();
        content.add(new Chunk(value + "\n", new Font(Font.HELVETICA, 18, Font.BOLD, Color.WHITE)));
        content.add(new Chunk(label, new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(200, 220, 240))));
        cell.addElement(content);
        t.addCell(cell);
    }

    private PdfPTable buildTwoColTable(String[] headers, String[][] rows) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(60);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.setSpacingAfter(14);
        addHdrCells(t, headers[0], headers[1]);

        boolean alt = false;
        for (String[] row : rows) {
            Color bg = alt ? ROW_ALT : ROW_WHITE;
            addBodyCell(t, row[0], bg);
            addBodyCellRight(t, row[1], bg);
            alt = !alt;
        }
        return t;
    }

    private void addHdrCells(PdfPTable t, String... headers) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, TABLE_HDR_FONT));
            c.setBackgroundColor(NAVY);
            c.setPadding(7);
            c.setBorder(Rectangle.NO_BORDER);
            t.addCell(c);
        }
    }

    private void addBodyCell(PdfPTable t, String val, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(val, TABLE_VAL_FONT));
        c.setBackgroundColor(bg);
        c.setPadding(6);
        c.setBorder(Rectangle.NO_BORDER);
        t.addCell(c);
    }

    private void addBodyCellRight(PdfPTable t, String val, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(val, TABLE_VAL_FONT));
        c.setBackgroundColor(bg);
        c.setPadding(6);
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c);
    }

    private void addInfoRow2(PdfPTable t, String label, String value) {
        PdfPCell lc = new PdfPCell(new Phrase(label, LABEL_FONT));
        lc.setBorder(Rectangle.NO_BORDER);
        lc.setPadding(4);
        PdfPCell vc = new PdfPCell(new Phrase(value, VALUE_BOLD));
        vc.setBorder(Rectangle.NO_BORDER);
        vc.setPadding(4);
        t.addCell(lc);
        t.addCell(vc);
    }

    // ── Data helpers ─────────────────────────────────────────────────────────

    private long count(List<Interview> all, InterviewStatus status) {
        return all.stream().filter(i -> i.getStatus() == status).count();
    }

    private long verdictCount(List<Interview> all, ReadinessVerdict v) {
        return all.stream().filter(i -> i.getFinalVerdict() == v).count();
    }

    private boolean inRange(Instant t, Instant s, Instant e) {
        return !t.isBefore(s) && t.isBefore(e);
    }

    private String pct(double v) {
        return String.format("%.1f%%", v);
    }

    private String fmt(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    private String formatNumber(long n) {
        return String.format("%,d", n);
    }

    private String formatCost(Object raw) {
        if (raw == null) return "N/A";
        try {
            return "$" + new BigDecimal(raw.toString())
                    .setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            return raw.toString();
        }
    }

    private long longVal(Map<String, Object> m, String key) {
        if (m == null) return 0;
        Object v = m.get(key);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e2) { return 0; }
    }

    private String str(Object o) { return o != null ? o.toString() : ""; }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeCall(java.util.function.Supplier<Map<String, Object>> fn) {
        try { return fn.get(); }
        catch (Exception e) { log.warn("Token analytics call failed: {}", e.getMessage()); return Map.of(); }
    }

    // ── Page footer ──────────────────────────────────────────────────────────

    private static class FooterPageEvent extends PdfPageEventHelper {
        private final LocalDate from;
        private final LocalDate to;

        FooterPageEvent(LocalDate from, LocalDate to) {
            this.from = from;
            this.to   = to;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            String text = "BenchReadiness Management Report  |  " +
                    from.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + " – " +
                    to.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) +
                    "  |  Page " + writer.getPageNumber() +
                    "  |  Generated " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
            cb.beginText();
            try { cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false), 7); }
            catch (Exception ex) { return; }
            cb.setColorFill(new Color(150, 150, 150));
            cb.setTextMatrix(document.left(), document.bottom() - 20);
            cb.showText(text);
            cb.endText();
        }
    }
}
