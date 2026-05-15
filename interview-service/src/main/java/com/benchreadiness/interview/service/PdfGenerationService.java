package com.benchreadiness.interview.service;

import com.benchreadiness.interview.dto.CandidateReviewSummary;
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
public class PdfGenerationService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(41, 128, 185));
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(52, 73, 94));
    private static final Font SUBSECTION_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(44, 62, 80));
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(100, 100, 100));

    private static final Color HEADER_BG = new Color(236, 240, 241);
    private static final Color SUCCESS_COLOR = new Color(39, 174, 96);
    private static final Color WARNING_COLOR = new Color(230, 126, 34);
    private static final Color DANGER_COLOR = new Color(231, 76, 60);

    public byte[] generateCandidateReviewPdf(CandidateReviewSummary summary) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        PdfWriter.getInstance(document, baos);
        
        document.open();
        
        addHeader(document, summary.getCandidateInfo());
        addOverview(document, summary.getOverviewStats());
        addInterviews(document, summary.getInterviews());
        addFooter(document);
        
        document.close();
        return baos.toByteArray();
    }

    private void addHeader(Document document, CandidateReviewSummary.CandidateInfo info) throws DocumentException {
        Paragraph title = new Paragraph("CANDIDATE REVIEW SUMMARY", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        addInfoRow(table, "Name:", info.getName());
        addInfoRow(table, "Email:", info.getEmail());
        addInfoRow(table, "Skill Set:", info.getSkillSet());
        addInfoRow(table, "Experience:", String.format("%.1f years (Portrayed: %.1f)", info.getYoeActual(), info.getYoePortrayed()));
        addInfoRow(table, "Batch:", info.getBatch());
        addInfoRow(table, "Source:", info.getSource());

        document.add(table);
        addSeparator(document);
    }

    private void addOverview(Document document, CandidateReviewSummary.OverviewStats stats) throws DocumentException {
        Paragraph section = new Paragraph("PERFORMANCE OVERVIEW", SECTION_FONT);
        section.setSpacingBefore(10);
        section.setSpacingAfter(10);
        document.add(section);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        addStatCell(table, "Total Interviews", String.valueOf(stats.getTotalInterviews()));
        addStatCell(table, "Ready Count", String.valueOf(stats.getReadyCount()));
        addStatCell(table, "Success Rate", String.format("%.1f%%", stats.getSuccessRate()));

        document.add(table);
        addSeparator(document);
    }

    private void addInterviews(Document document, List<CandidateReviewSummary.InterviewDetail> interviews) throws DocumentException {
        Paragraph section = new Paragraph("INTERVIEW DETAILS", SECTION_FONT);
        section.setSpacingBefore(10);
        section.setSpacingAfter(15);
        document.add(section);

        int count = 1;
        for (CandidateReviewSummary.InterviewDetail interview : interviews) {
            addInterviewSection(document, interview, count++);
        }
    }

    private void addInterviewSection(Document document, CandidateReviewSummary.InterviewDetail interview, int number) throws DocumentException {
        Paragraph interviewTitle = new Paragraph(String.format("Interview #%d", number), SUBSECTION_FONT);
        interviewTitle.setSpacingBefore(15);
        interviewTitle.setSpacingAfter(8);
        document.add(interviewTitle);

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(10);

        addInfoRow(infoTable, "Date:", formatDate(interview.getInterviewDate()));
        addInfoRow(infoTable, "Position:", interview.getJdTitle());
        addInfoRow(infoTable, "Mode:", interview.getInterviewMode());
        addInfoRow(infoTable, "Verdict:", interview.getVerdict(), getVerdictColor(interview.getVerdict()));

        document.add(infoTable);

        if (interview.getCategoryScores() != null && !interview.getCategoryScores().isEmpty()) {
            addCategoryScores(document, interview.getCategoryScores());
        }

        if (interview.getProsAndCons() != null && !interview.getProsAndCons().isEmpty()) {
            addProsAndCons(document, interview.getProsAndCons());
        }

        if (interview.getResumeConsistency() != null) {
            addResumeConsistency(document, interview.getResumeConsistency());
        }

        if (interview.getBehavioralSignals() != null) {
            addBehavioralSignals(document, interview.getBehavioralSignals());
        }

        if (interview.getRoadmap() != null && !interview.getRoadmap().isEmpty()) {
            addRoadmap(document, interview.getRoadmap());
        }

        addSeparator(document);
    }

    private void addCategoryScores(Document document, List<CandidateReviewSummary.CategoryScore> scores) throws DocumentException {
        Paragraph subheading = new Paragraph("Category Scores", BOLD_FONT);
        subheading.setSpacingBefore(10);
        subheading.setSpacingAfter(5);
        document.add(subheading);

        PdfPTable table = new PdfPTable(new float[]{3, 1, 4});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);

        addTableHeader(table, "Category", "Score", "Gaps to Address");

        for (CandidateReviewSummary.CategoryScore score : scores) {
            addCell(table, capitalize(score.getDimension()), NORMAL_FONT);
            addCell(table, String.valueOf(score.getValue()), BOLD_FONT, getScoreColor(score.getValue()));
            addCell(table, score.getGap() != null && !score.getGap().isEmpty() ? score.getGap() : "None identified", SMALL_FONT);
        }

        document.add(table);
    }

    private void addProsAndCons(Document document, List<CandidateReviewSummary.ProCon> prosAndCons) throws DocumentException {
        Paragraph subheading = new Paragraph("Strengths & Weaknesses", BOLD_FONT);
        subheading.setSpacingBefore(10);
        subheading.setSpacingAfter(5);
        document.add(subheading);

        for (CandidateReviewSummary.ProCon pc : prosAndCons) {
            Paragraph category = new Paragraph(pc.getCategory(), BOLD_FONT);
            category.setSpacingBefore(5);
            document.add(category);

            if (pc.getPros() != null && !pc.getPros().isEmpty()) {
                Paragraph prosTitle = new Paragraph("✓ Strengths:", new Font(Font.HELVETICA, 9, Font.BOLD, SUCCESS_COLOR));
                prosTitle.setSpacingBefore(3);
                document.add(prosTitle);
                for (String pro : pc.getPros()) {
                    Paragraph item = new Paragraph("  • " + pro, SMALL_FONT);
                    item.setIndentationLeft(15);
                    document.add(item);
                }
            }

            if (pc.getCons() != null && !pc.getCons().isEmpty()) {
                Paragraph consTitle = new Paragraph("✗ Weaknesses:", new Font(Font.HELVETICA, 9, Font.BOLD, DANGER_COLOR));
                consTitle.setSpacingBefore(3);
                document.add(consTitle);
                for (String con : pc.getCons()) {
                    Paragraph item = new Paragraph("  • " + con, SMALL_FONT);
                    item.setIndentationLeft(15);
                    document.add(item);
                }
            }
        }
        document.add(new Paragraph(" "));
    }

    private void addResumeConsistency(Document document, CandidateReviewSummary.ResumeConsistency consistency) throws DocumentException {
        Paragraph subheading = new Paragraph("Resume Consistency", BOLD_FONT);
        subheading.setSpacingBefore(10);
        subheading.setSpacingAfter(5);
        document.add(subheading);

        Paragraph score = new Paragraph("Consistency Score: " + consistency.getConsistencyScore() + "/5", NORMAL_FONT);
        document.add(score);

        if (consistency.getFlags() != null && !consistency.getFlags().isEmpty()) {
            Paragraph flagsTitle = new Paragraph("⚠ Flags:", new Font(Font.HELVETICA, 9, Font.BOLD, WARNING_COLOR));
            flagsTitle.setSpacingBefore(5);
            document.add(flagsTitle);
            for (String flag : consistency.getFlags()) {
                Paragraph item = new Paragraph("  • " + flag, SMALL_FONT);
                item.setIndentationLeft(15);
                document.add(item);
            }
        }
        document.add(new Paragraph(" "));
    }

    private void addBehavioralSignals(Document document, CandidateReviewSummary.BehavioralSignals signals) throws DocumentException {
        Paragraph subheading = new Paragraph("Behavioral Signals", BOLD_FONT);
        subheading.setSpacingBefore(10);
        subheading.setSpacingAfter(5);
        document.add(subheading);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);

        addInfoRow(table, "Ownership:", signals.getOwnershipLevel());
        addInfoRow(table, "Learning Agility:", signals.getLearningAgility());
        addInfoRow(table, "Communication:", signals.getCommunicationStructure());
        addInfoRow(table, "Confidence:", signals.getConfidenceCalibration());

        document.add(table);

        if (signals.getSummary() != null) {
            Paragraph summary = new Paragraph(signals.getSummary(), SMALL_FONT);
            summary.setSpacingBefore(5);
            document.add(summary);
        }
    }

    private void addRoadmap(Document document, List<CandidateReviewSummary.RoadmapItem> roadmap) throws DocumentException {
        Paragraph subheading = new Paragraph("7-Day Improvement Roadmap", BOLD_FONT);
        subheading.setSpacingBefore(10);
        subheading.setSpacingAfter(5);
        document.add(subheading);

        for (CandidateReviewSummary.RoadmapItem item : roadmap) {
            Paragraph day = new Paragraph(item.getDay() + " - " + item.getCategory(), new Font(Font.HELVETICA, 10, Font.BOLD, new Color(41, 128, 185)));
            day.setSpacingBefore(5);
            document.add(day);

            Paragraph focus = new Paragraph("Focus: " + item.getFocus(), SMALL_FONT);
            focus.setIndentationLeft(10);
            document.add(focus);

            if (item.getWhyItMatters() != null) {
                Paragraph why = new Paragraph("Why: " + item.getWhyItMatters(), SMALL_FONT);
                why.setIndentationLeft(10);
                document.add(why);
            }

            if (item.getExercise() != null) {
                Paragraph exercise = new Paragraph("Exercise: " + item.getExercise(), SMALL_FONT);
                exercise.setIndentationLeft(10);
                document.add(exercise);
            }
        }
    }

    private void addFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph("Generated on " + formatDate(Instant.now()) + " | Bench Readiness Platform", SMALL_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        document.add(footer);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        addInfoRow(table, label, value, null);
    }

    private void addInfoRow(PdfPTable table, String label, String value, Color valueColor) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, BOLD_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(HEADER_BG);
        table.addCell(labelCell);

        Font valueFont = valueColor != null ? new Font(Font.HELVETICA, 10, Font.BOLD, valueColor) : NORMAL_FONT;
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    private void addStatCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10);
        cell.setBackgroundColor(HEADER_BG);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph labelPara = new Paragraph(label, SMALL_FONT);
        labelPara.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(labelPara);

        Paragraph valuePara = new Paragraph(value, new Font(Font.HELVETICA, 16, Font.BOLD, new Color(41, 128, 185)));
        valuePara.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(valuePara);

        table.addCell(cell);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, BOLD_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text, Font font) {
        addCell(table, text, font, null);
    }

    private void addCell(PdfPTable table, String text, Font font, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        if (bgColor != null) {
            cell.setBackgroundColor(bgColor);
        }
        table.addCell(cell);
    }

    private void addSeparator(Document document) throws DocumentException {
        LineSeparator separator = new LineSeparator();
        separator.setLineColor(new Color(189, 195, 199));
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));
    }

    private Color getVerdictColor(String verdict) {
        if (verdict == null) return Color.BLACK;
        return switch (verdict) {
            case "READY" -> SUCCESS_COLOR;
            case "NEEDS_1_WEEK_PREP" -> WARNING_COLOR;
            case "NEEDS_RESKILLING", "MISMATCH_WITH_JD", "WITHDRAWN" -> DANGER_COLOR;
            default -> Color.BLACK;
        };
    }

    private Color getScoreColor(int score) {
        if (score >= 4) return SUCCESS_COLOR;
        if (score >= 3) return WARNING_COLOR;
        return DANGER_COLOR;
    }

    private String formatDate(Instant instant) {
        if (instant == null) return "N/A";
        return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("_", " ");
    }

    private String cleanJsonArray(String jsonArray) {
        if (jsonArray == null) return "-";
        return jsonArray.replace("[", "").replace("]", "").replace("\"", "").replace(",", ", ");
    }
}
