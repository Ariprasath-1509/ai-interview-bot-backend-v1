package com.benchreadiness.interview.service;

import com.benchreadiness.interview.dto.ClientBriefDto;
import com.benchreadiness.interview.service.ClientBriefService.ClientBriefContext;
import com.benchreadiness.interview.service.ClientBriefService.ClientBriefPdfContext;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ClientBriefPdfGenerationService {

    private static final Color BRAND_PRIMARY = new Color(15, 76, 129);
    private static final Color BRAND_ACCENT = new Color(0, 102, 153);
    private static final Color HEADER_BG = new Color(245, 247, 250);
    private static final Color SUCCESS = new Color(22, 128, 73);
    private static final Color WARNING = new Color(180, 120, 0);
    private static final Color MUTED = new Color(100, 110, 120);

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 22, Font.BOLD, BRAND_PRIMARY);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 11, Font.NORMAL, MUTED);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_PRIMARY);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
    private static final Font REC_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, Color.WHITE);

    public byte[] generateClientBriefPdf(ClientBriefPdfContext pdfContext) throws Exception {
        ClientBriefDto brief = pdfContext.brief();
        ClientBriefContext ctx = pdfContext.context();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 45, 45, 55, 50);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        addCoverHeader(document, ctx);
        addRecommendationBanner(document, brief.getRecommendation());
        addExecutiveSummary(document, brief.getExecutiveSummary());
        addCandidateProfile(document, ctx);
        addBulletSection(document, "Key Strengths", brief.getKeyStrengths(), SUCCESS);
        addBulletSection(document, "Areas to Note", brief.getAreasToNote(), WARNING);
        addTechnicalFit(document, brief.getTechnicalFit());
        addInterviewPerformance(document, brief.getInterviewPerformance());
        addRecommendationDetails(document, brief);
        addConfidentialFooter(document, brief);

        document.close();
        return baos.toByteArray();
    }

    private void addCoverHeader(Document document, ClientBriefContext ctx) throws DocumentException {
        Paragraph brand = new Paragraph("BENCH READINESS", new Font(Font.HELVETICA, 10, Font.BOLD, BRAND_ACCENT));
        brand.setSpacingAfter(4);
        document.add(brand);

        Paragraph title = new Paragraph("Candidate Evaluation Brief", TITLE_FONT);
        title.setSpacingAfter(4);
        document.add(title);

        Paragraph subtitle = new Paragraph(
            "Confidential — Prepared for client review | " + formatDisplayDate(ctx.interviewDate()),
            SUBTITLE_FONT);
        subtitle.setSpacingAfter(16);
        document.add(subtitle);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        addProfileRow(table, "Candidate", ctx.candidateName());
        addProfileRow(table, "Position Assessed", ctx.jdTitle());
        addProfileRow(table, "Interview Mode", formatLabel(ctx.interviewMode()));
        addProfileRow(table, "Primary Skill", nullToDash(ctx.skillSet()));
        document.add(table);
        addSeparator(document);
    }

    private void addRecommendationBanner(Document document, String recommendation) throws DocumentException {
        Color bg = recommendationColor(recommendation);
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(14);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(12);

        Paragraph label = new Paragraph("Overall Recommendation", new Font(Font.HELVETICA, 9, Font.NORMAL, Color.WHITE));
        label.setSpacingAfter(4);
        cell.addElement(label);

        Paragraph value = new Paragraph(formatRecommendation(recommendation), REC_FONT);
        cell.addElement(value);
        banner.addCell(cell);
        document.add(banner);
    }

    private void addExecutiveSummary(Document document, String summary) throws DocumentException {
        addSectionTitle(document, "Executive Summary");
        Paragraph body = new Paragraph(summary != null && !summary.isBlank()
            ? summary : "No executive summary provided.", BODY_FONT);
        body.setLeading(15);
        body.setSpacingAfter(12);
        document.add(body);
        addSeparator(document);
    }

    private void addCandidateProfile(Document document, ClientBriefContext ctx) throws DocumentException {
        addSectionTitle(document, "Candidate Profile");
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);

        addProfileRow(table, "Internal Verdict", formatVerdict(ctx.verdict()));
        if (ctx.yoePortrayed() != null) {
            addProfileRow(table, "Experience", String.format("%.1f years", ctx.yoePortrayed()));
        }
        addProfileRow(table, "Assessment Date", formatDisplayDate(ctx.interviewDate()));
        document.add(table);
    }

    private void addBulletSection(Document document, String title, List<String> items, Color bulletColor)
            throws DocumentException {
        addSectionTitle(document, title);
        if (items == null || items.isEmpty()) {
            document.add(new Paragraph("Not specified.", BODY_FONT));
            document.add(spacer());
            return;
        }
        com.lowagie.text.List list = new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);
        list.setListSymbol(new Chunk("•  ", new Font(Font.HELVETICA, 12, Font.BOLD, bulletColor)));
        for (String item : items) {
            ListItem li = new ListItem(item, BODY_FONT);
            li.setLeading(14);
            list.add(li);
        }
        document.add(list);
        document.add(spacer());
    }

    private void addTechnicalFit(Document document, ClientBriefDto.TechnicalFit fit) throws DocumentException {
        addSectionTitle(document, "Technical Fit Assessment");
        if (fit == null) {
            document.add(new Paragraph("Not specified.", BODY_FONT));
            document.add(spacer());
            return;
        }

        Paragraph overall = new Paragraph("Overall Fit: " + nullToDash(fit.getOverall()), BOLD_FONT);
        overall.setSpacingAfter(8);
        document.add(overall);

        if (fit.getHighlights() != null && !fit.getHighlights().isEmpty()) {
            Paragraph h = new Paragraph("Alignment Highlights", BOLD_FONT);
            h.setSpacingBefore(4);
            document.add(h);
            addSimpleBullets(document, fit.getHighlights());
        }
        if (fit.getGaps() != null && !fit.getGaps().isEmpty()) {
            Paragraph g = new Paragraph("Development Areas", BOLD_FONT);
            g.setSpacingBefore(8);
            document.add(g);
            addSimpleBullets(document, fit.getGaps());
        }
        document.add(spacer());
        addSeparator(document);
    }

    private void addInterviewPerformance(Document document, ClientBriefDto.InterviewPerformance perf)
            throws DocumentException {
        addSectionTitle(document, "Interview Performance");
        if (perf == null) {
            document.add(new Paragraph("Not specified.", BODY_FONT));
            document.add(spacer());
            return;
        }

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        addProfileRow(table, "Communication", nullToDash(perf.getCommunication()));
        addProfileRow(table, "Problem Solving", nullToDash(perf.getProblemSolving()));
        addProfileRow(table, "Overall Rating", nullToDash(perf.getOverallRating()));
        document.add(table);
        addSeparator(document);
    }

    private void addRecommendationDetails(Document document, ClientBriefDto brief) throws DocumentException {
        addSectionTitle(document, "Recommendation Details");
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        addProfileRow(table, "Recommended For", nullToDash(brief.getRecommendedFor()));
        addProfileRow(table, "Suggested Next Step", nullToDash(brief.getSuggestedNextStep()));
        document.add(table);
    }

    private void addConfidentialFooter(Document document, ClientBriefDto brief) throws DocumentException {
        document.add(spacer());
        Paragraph notice = new Paragraph(
            "CONFIDENTIAL: This document contains proprietary assessment information prepared by Bench Readiness. "
                + "It is intended solely for the recipient organization's hiring evaluation and must not be "
                + "redistributed without authorization.",
            SMALL_FONT);
        notice.setSpacingAfter(8);
        document.add(notice);

        String edited = brief.getLastEditedByName() != null && !brief.getLastEditedByName().isBlank()
            ? " | Reviewed by: " + brief.getLastEditedByName() : "";
        Paragraph footer = new Paragraph(
            "Generated " + formatDisplayDate(Instant.now().toString()) + edited
                + " | Bench Readiness Platform",
            SMALL_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private void addSimpleBullets(Document document, List<String> items) throws DocumentException {
        com.lowagie.text.List list = new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);
        list.setListSymbol(new Chunk("•  ", BOLD_FONT));
        for (String item : items) {
            list.add(new ListItem(item, BODY_FONT));
        }
        document.add(list);
    }

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph section = new Paragraph(title, SECTION_FONT);
        section.setSpacingBefore(6);
        section.setSpacingAfter(8);
        document.add(section);
    }

    private void addProfileRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, BOLD_FONT));
        labelCell.setBackgroundColor(HEADER_BG);
        labelCell.setPadding(8);
        labelCell.setBorderColor(new Color(220, 225, 230));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(nullToDash(value), BODY_FONT));
        valueCell.setPadding(8);
        valueCell.setBorderColor(new Color(220, 225, 230));
        table.addCell(valueCell);
    }

    private void addSeparator(Document document) throws DocumentException {
        LineSeparator separator = new LineSeparator();
        separator.setLineColor(new Color(220, 225, 230));
        document.add(new Chunk(separator));
        document.add(spacer());
    }

    private Paragraph spacer() {
        return new Paragraph(" ");
    }

    private Color recommendationColor(String recommendation) {
        if (recommendation == null) return BRAND_PRIMARY;
        return switch (recommendation.toUpperCase()) {
            case "RECOMMENDED" -> SUCCESS;
            case "RECOMMENDED_WITH_CONDITIONS" -> WARNING;
            case "NOT_RECOMMENDED" -> new Color(180, 50, 50);
            default -> BRAND_PRIMARY;
        };
    }

    private String formatRecommendation(String recommendation) {
        if (recommendation == null || recommendation.isBlank()) return "Pending Review";
        return switch (recommendation.toUpperCase()) {
            case "RECOMMENDED" -> "Recommended";
            case "RECOMMENDED_WITH_CONDITIONS" -> "Recommended with Conditions";
            case "NOT_RECOMMENDED" -> "Not Recommended at This Time";
            default -> recommendation.replace('_', ' ');
        };
    }

    private String formatVerdict(String verdict) {
        if (verdict == null || verdict.isBlank()) return "—";
        return verdict.replace('_', ' ');
    }

    private String formatLabel(String value) {
        if (value == null || value.isBlank()) return "—";
        return value.replace('_', ' ');
    }

    private String nullToDash(String value) {
        return value != null && !value.isBlank() ? value : "—";
    }

    private String formatDisplayDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return DateTimeFormatter.ofPattern("dd MMM yyyy")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        }
        try {
            return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }
}
