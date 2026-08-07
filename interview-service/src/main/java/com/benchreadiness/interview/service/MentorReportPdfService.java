package com.benchreadiness.interview.service;

import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.repository.InterviewRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MentorReportPdfService {

    private static final Logger log = LoggerFactory.getLogger(MentorReportPdfService.class);

    private final InterviewRepository interviewRepository;
    private final ObjectMapper objectMapper;

    public MentorReportPdfService(InterviewRepository interviewRepository, ObjectMapper objectMapper) {
        this.interviewRepository = interviewRepository;
        this.objectMapper = objectMapper;
    }

    // ── Palette (teal/green theme — distinct from client brief's purple) ──────
    private static final Color HEADER_DARK   = new Color(10, 60, 75);
    private static final Color HEADER_MID    = new Color(13, 85, 100);
    private static final Color TEAL          = new Color(20, 120, 140);
    private static final Color TEAL_LIGHT    = new Color(45, 155, 170);
    private static final Color ACCENT_AMBER  = new Color(212, 168, 75);
    private static final Color CARD_BG       = new Color(255, 255, 255);
    private static final Color LIGHT_BG      = new Color(240, 252, 254);
    private static final Color CARD_BORDER   = new Color(180, 225, 232);
    private static final Color SUCCESS       = new Color(16, 114, 64);
    private static final Color SUCCESS_BG    = new Color(232, 253, 242);
    private static final Color SUCCESS_BORDER= new Color(167, 230, 200);
    private static final Color GAP          = new Color(155, 65, 10);
    private static final Color GAP_BG       = new Color(255, 247, 235);
    private static final Color GAP_BORDER   = new Color(253, 211, 154);
    private static final Color DANGER        = new Color(185, 28, 28);
    private static final Color DANGER_BG     = new Color(254, 242, 242);
    private static final Color MUTED         = new Color(90, 100, 110);
    private static final Color CHIP_BG       = new Color(15, 90, 110);
    private static final Color DIVIDER       = new Color(200, 235, 240);

    private static final Font F_BRAND     = new Font(Font.HELVETICA, 7,  Font.BOLD,   ACCENT_AMBER);
    private static final Font F_SUBTITLE  = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(180, 225, 235));
    private static final Font F_TITLE     = new Font(Font.HELVETICA, 24, Font.BOLD,   Color.WHITE);
    private static final Font F_CHIP_BOLD = new Font(Font.HELVETICA, 9,  Font.BOLD,   Color.WHITE);
    private static final Font F_SECTION   = new Font(Font.HELVETICA, 11, Font.BOLD,   HEADER_DARK);
    private static final Font F_SUB       = new Font(Font.HELVETICA, 10, Font.BOLD,   HEADER_DARK);
    private static final Font F_BODY      = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(28, 35, 42));
    private static final Font F_BODY_SM   = new Font(Font.HELVETICA, 9,  Font.NORMAL, new Color(50, 58, 68));
    private static final Font F_SMALL     = new Font(Font.HELVETICA, 8,  Font.NORMAL, MUTED);
    private static final Font F_SCORE_BIG = new Font(Font.HELVETICA, 26, Font.BOLD,   TEAL);
    private static final Font F_SCORE_DIM = new Font(Font.HELVETICA, 10, Font.BOLD,   TEAL_LIGHT);
    private static final Font F_FOOTER    = new Font(Font.HELVETICA, 7,  Font.NORMAL, MUTED);

    // ── Public API ────────────────────────────────────────────────────────────

    public record MentorReportContext(
        String candidateName,
        String candidateEmail,
        String jdTitle,
        String interviewDate,
        String status,
        String proposedVerdict,
        String finalVerdict,
        String signOffNote,
        JsonNode assessment,          // full ai assessment node
        List<ScoreRow> scores,
        List<CodeSub> codeSubmissions
    ) {}

    public record ScoreRow(String dimension, int value, int max, String rationale, String evidence, String gap, String confidence) {}
    public record CodeSub(String language, String question, String code, String correctness, int score, String feedback) {}

    /** Build context from interview + assessment data already parsed from transcript. */
    public MentorReportContext buildContext(String interviewId, String userId) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));

        JsonNode assessment = parseAssessment(interview.getTranscriptJson());
        List<ScoreRow> scores = parseScores(assessment);
        List<CodeSub> codeSubs = parseCodeSubmissions(interview.getTranscriptJson());
        String interviewDate = interview.getCreatedAt() != null
            ? DateTimeFormatter.ofPattern("d MMM yyyy")
                .withZone(ZoneId.systemDefault())
                .format(interview.getCreatedAt())
            : null;

        return new MentorReportContext(
            safe(candidateName(interview)),
            safe(candidateEmail(interview)),
            safe(jdTitle(interview)),
            interviewDate,
            interview.getStatus() != null ? interview.getStatus().name() : null,
            interview.getProposedVerdict() != null ? interview.getProposedVerdict().name() : null,
            interview.getFinalVerdict() != null ? interview.getFinalVerdict().name() : null,
            signOffNote(interview.getTranscriptJson()),
            assessment,
            scores,
            codeSubs
        );
    }

    public byte[] generatePdf(MentorReportContext ctx) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 40, 64);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new MentorPageEvent(ctx.candidateName()));
        doc.open();

        try {
            addHeader(doc, ctx);
            addPurposeBanner(doc);
            addVerdictCard(doc, ctx);
            addScoreGrid(doc, ctx.scores());
            addStrengthsGaps(doc, ctx.assessment());
            addBehavioralSignals(doc, ctx.assessment());
            addResumeConsistency(doc, ctx.assessment());
            addSpeechAnalytics(doc, ctx.assessment());
            if (!ctx.codeSubmissions().isEmpty()) addCodeSection(doc, ctx.codeSubmissions());
            addRoadmap(doc, ctx.assessment());
            addClosing(doc, ctx);
        } catch (Exception e) {
            log.error("Mentor report PDF failed: {}", e.getMessage(), e);
            throw e;
        }

        doc.close();
        return baos.toByteArray();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, MentorReportContext ctx) throws DocumentException {
        PdfPTable outer = new PdfPTable(1);
        outer.setWidthPercentage(100);
        outer.setSpacingAfter(16);

        // Top band
        PdfPCell top = new PdfPCell();
        top.setBackgroundColor(HEADER_DARK);
        top.setBorder(Rectangle.NO_BORDER);
        top.setPadding(18);
        top.addElement(new Paragraph("BENCH READINESS  ·  MENTOR REPORT", F_BRAND));
        Paragraph sub = new Paragraph("Candidate Development Report — Confidential", F_SUBTITLE);
        sub.setSpacingBefore(4);
        sub.setSpacingAfter(6);
        top.addElement(sub);
        top.addElement(new Paragraph(ctx.candidateName(), F_TITLE));
        outer.addCell(top);

        // Meta chips
        PdfPTable chips = new PdfPTable(3);
        chips.setWidthPercentage(100);
        chips.setWidths(new float[]{1f, 1f, 1f});
        addChip(chips, "Role", ctx.jdTitle());
        addChip(chips, "Interview date", ctx.interviewDate());
        addChip(chips, "Email", ctx.candidateEmail());

        PdfPCell bottom = new PdfPCell(chips);
        bottom.setBackgroundColor(HEADER_MID);
        bottom.setBorder(Rectangle.NO_BORDER);
        bottom.setPadding(14);
        outer.addCell(bottom);

        doc.add(outer);
    }

    private void addChip(PdfPTable table, String label, String value) {
        if (value == null || value.isBlank() || value.equals("-")) {
            PdfPCell e = new PdfPCell(new Phrase(" "));
            e.setBorder(Rectangle.NO_BORDER);
            e.setPadding(4);
            table.addCell(e);
            return;
        }
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(CHIP_BG);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(8);
        Paragraph lbl = new Paragraph(label.toUpperCase(), new Font(Font.HELVETICA, 7, Font.BOLD, ACCENT_AMBER));
        lbl.setSpacingAfter(3);
        cell.addElement(lbl);
        cell.addElement(new Paragraph(value, F_CHIP_BOLD));
        table.addCell(cell);
    }

    // ── Purpose banner ────────────────────────────────────────────────────────

    private void addPurposeBanner(Document doc) throws DocumentException {
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(14);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT_BG);
        cell.setBorderColor(CARD_BORDER);
        cell.setBorderWidth(0.75f);
        cell.setPadding(12);
        Paragraph p = new Paragraph(
            "This report is prepared for the candidate's mentor or coach. " +
            "It contains honest, actionable feedback drawn directly from the AI interview assessment to guide targeted improvement. " +
            "Please treat this as a confidential coaching document.", F_BODY_SM);
        p.setLeading(15);
        cell.addElement(p);
        banner.addCell(cell);
        doc.add(banner);
    }

    // ── Verdict card ──────────────────────────────────────────────────────────

    private void addVerdictCard(Document doc, MentorReportContext ctx) throws DocumentException {
        doc.add(sectionTitle("Assessment Outcome"));

        String verdict = ctx.finalVerdict() != null ? ctx.finalVerdict() : ctx.proposedVerdict();
        Color bg;
        Color border;
        Color fg;
        String icon;
        if (verdict == null) { verdict = "PENDING"; }
        switch (verdict) {
            case "READY" -> { bg = SUCCESS_BG; border = SUCCESS_BORDER; fg = SUCCESS; icon = "✔  READY FOR PLACEMENT"; }
            case "NEEDS_1_WEEK_PREP" -> { bg = GAP_BG; border = GAP_BORDER; fg = GAP; icon = "⚡  NEEDS 1-WEEK PREPARATION"; }
            case "NEEDS_RESKILLING" -> { bg = DANGER_BG; border = new Color(250, 200, 200); fg = DANGER; icon = "⚠  NEEDS RESKILLING"; }
            default -> { bg = new Color(240, 245, 255); border = new Color(200, 215, 245); fg = new Color(50, 80, 160); icon = "◉  " + verdict.replace("_", " "); }
        }

        PdfPTable card = new PdfPTable(new float[]{0.04f, 0.96f});
        card.setWidthPercentage(100);
        card.setSpacingAfter(12);

        PdfPCell stripe = new PdfPCell();
        stripe.setBackgroundColor(fg);
        stripe.setBorder(Rectangle.NO_BORDER);
        card.addCell(stripe);

        PdfPCell content = new PdfPCell();
        content.setBackgroundColor(bg);
        content.setBorderColor(border);
        content.setBorderWidth(0.75f);
        content.setPadding(14);
        content.addElement(new Paragraph(icon, new Font(Font.HELVETICA, 13, Font.BOLD, fg)));
        if (ctx.signOffNote() != null && !ctx.signOffNote().isBlank()) {
            Paragraph note = new Paragraph("Reviewer note: " + ctx.signOffNote(),
                new Font(Font.HELVETICA, 9, Font.ITALIC, fg));
            note.setSpacingBefore(6);
            note.setLeading(14);
            content.addElement(note);
        }
        card.addCell(content);
        doc.add(card);

        // Overall AI summary
        String summary = textNode(ctx.assessment(), "summary");
        if (!summary.isBlank()) {
            PdfPTable sumBox = new PdfPTable(1);
            sumBox.setWidthPercentage(100);
            sumBox.setSpacingAfter(12);
            PdfPCell sumCell = new PdfPCell();
            sumCell.setBackgroundColor(CARD_BG);
            sumCell.setBorderColor(CARD_BORDER);
            sumCell.setBorderWidth(0.75f);
            sumCell.setPadding(14);
            Paragraph sumP = new Paragraph(summary, F_BODY);
            sumP.setLeading(16);
            sumCell.addElement(sumP);
            sumBox.addCell(sumCell);
            doc.add(sumBox);
        }
    }

    // ── Score grid ────────────────────────────────────────────────────────────

    private void addScoreGrid(Document doc, List<ScoreRow> scores) throws DocumentException {
        if (scores.isEmpty()) return;
        doc.add(sectionTitle("Skill Scores"));

        for (ScoreRow s : scores) {
            PdfPTable row = new PdfPTable(new float[]{0.22f, 0.78f});
            row.setWidthPercentage(100);
            row.setSpacingAfter(8);
            row.setKeepTogether(true);

            // Score badge cell
            PdfPCell badge = new PdfPCell();
            badge.setBackgroundColor(scoreColor(s.value(), s.max()));
            badge.setBorder(Rectangle.NO_BORDER);
            badge.setHorizontalAlignment(Element.ALIGN_CENTER);
            badge.setVerticalAlignment(Element.ALIGN_MIDDLE);
            badge.setPadding(12);
            Paragraph scoreNum = new Paragraph(s.value() + "/" + s.max(),
                new Font(Font.HELVETICA, 20, Font.BOLD, Color.WHITE));
            scoreNum.setAlignment(Element.ALIGN_CENTER);
            badge.addElement(scoreNum);
            Paragraph dimLabel = new Paragraph(s.dimension(),
                new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE));
            dimLabel.setAlignment(Element.ALIGN_CENTER);
            dimLabel.setSpacingBefore(4);
            badge.addElement(dimLabel);
            if (s.confidence() != null && !s.confidence().isBlank()) {
                Paragraph conf = new Paragraph(s.confidence().toUpperCase() + " CONFIDENCE",
                    new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(200, 230, 235)));
                conf.setAlignment(Element.ALIGN_CENTER);
                conf.setSpacingBefore(2);
                badge.addElement(conf);
            }
            row.addCell(badge);

            // Detail cell
            PdfPCell detail = new PdfPCell();
            detail.setBackgroundColor(CARD_BG);
            detail.setBorderColor(CARD_BORDER);
            detail.setBorderWidth(0.75f);
            detail.setBorderWidthLeft(0f);
            detail.setPadding(12);
            detail.setPaddingLeft(14);

            if (s.rationale() != null && !s.rationale().isBlank()) {
                Paragraph rat = new Paragraph(s.rationale(), F_BODY_SM);
                rat.setLeading(14);
                detail.addElement(rat);
            }
            if (s.evidence() != null && !s.evidence().isBlank()) {
                Paragraph ev = new Paragraph("\"" + s.evidence() + "\"",
                    new Font(Font.HELVETICA, 8, Font.ITALIC, MUTED));
                ev.setSpacingBefore(5);
                ev.setLeading(13);
                detail.addElement(ev);
            }
            if (s.gap() != null && !s.gap().isBlank()) {
                Paragraph gp = new Paragraph("Gap: " + s.gap(),
                    new Font(Font.HELVETICA, 8, Font.BOLD, GAP));
                gp.setSpacingBefore(5);
                detail.addElement(gp);
            }
            row.addCell(detail);
            doc.add(row);
        }
    }

    // ── Strengths & Gaps ──────────────────────────────────────────────────────

    private void addStrengthsGaps(Document doc, JsonNode assessment) throws DocumentException {
        if (assessment == null || assessment.isMissingNode()) return;
        List<String> strengths = stringList(assessment, "strengths");
        List<String> gaps = stringList(assessment, "gaps");
        if (strengths.isEmpty() && gaps.isEmpty()) return;

        doc.add(sectionTitle("Strengths & Growth Areas"));

        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[]{1f, 1f});
        grid.setSpacingAfter(12);
        grid.addCell(bulletCard("Demonstrated Strengths", strengths, SUCCESS_BG, SUCCESS_BORDER, SUCCESS));
        grid.addCell(bulletCard("Priority Growth Areas", gaps.isEmpty()
            ? List.of("No specific gaps identified.") : gaps, GAP_BG, GAP_BORDER, GAP));
        doc.add(grid);
    }

    private PdfPCell bulletCard(String title, List<String> items, Color bg, Color border, Color titleColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        cell.setBorderWidth(0.75f);
        cell.setPadding(12);
        Paragraph titleP = new Paragraph(title.toUpperCase(),
            new Font(Font.HELVETICA, 7, Font.BOLD, titleColor));
        titleP.setSpacingAfter(8);
        cell.addElement(titleP);
        for (String item : items) {
            Paragraph p = new Paragraph("•  " + item, F_BODY_SM);
            p.setLeading(15);
            p.setSpacingAfter(4);
            cell.addElement(p);
        }
        return cell;
    }

    // ── Behavioral signals ────────────────────────────────────────────────────

    private void addBehavioralSignals(Document doc, JsonNode assessment) throws DocumentException {
        if (assessment == null || !assessment.has("behavioralSignals")) return;
        JsonNode bs = assessment.get("behavioralSignals");
        doc.add(sectionTitle("Behavioral Signals"));

        String[][] signals = {
            {"Ownership", textNodeFrom(bs, "ownership")},
            {"Learning Agility", textNodeFrom(bs, "learningAgility")},
            {"Communication", textNodeFrom(bs, "communication")},
            {"Confidence Calibration", textNodeFrom(bs, "confidenceCalibration")},
        };

        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[]{1f, 1f});
        grid.setSpacingAfter(12);

        for (String[] sig : signals) {
            if (sig[1].isBlank()) continue;
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(LIGHT_BG);
            cell.setBorderColor(CARD_BORDER);
            cell.setBorderWidth(0.75f);
            cell.setPadding(10);
            Paragraph lbl = new Paragraph(sig[0].toUpperCase(),
                new Font(Font.HELVETICA, 7, Font.BOLD, MUTED));
            lbl.setSpacingAfter(4);
            cell.addElement(lbl);
            Color levelColor = sig[1].equalsIgnoreCase("high") ? SUCCESS
                : sig[1].equalsIgnoreCase("low") ? DANGER : TEAL;
            cell.addElement(new Paragraph(sig[1].substring(0, 1).toUpperCase() + sig[1].substring(1),
                new Font(Font.HELVETICA, 11, Font.BOLD, levelColor)));
            grid.addCell(cell);
        }
        // pad to even columns
        if (grid.getRows().size() * 2 % 2 != 0) {
            PdfPCell empty = new PdfPCell(new Phrase(" "));
            empty.setBorder(Rectangle.NO_BORDER);
            grid.addCell(empty);
        }
        doc.add(grid);

        String bsSummary = textNodeFrom(bs, "summary");
        if (!bsSummary.isBlank()) {
            PdfPTable box = new PdfPTable(1);
            box.setWidthPercentage(100);
            box.setSpacingAfter(12);
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(CARD_BG);
            cell.setBorderColor(CARD_BORDER);
            cell.setBorderWidth(0.75f);
            cell.setPadding(12);
            Paragraph p = new Paragraph(bsSummary, F_BODY_SM);
            p.setLeading(15);
            cell.addElement(p);
            box.addCell(cell);
            doc.add(box);
        }
    }

    // ── Resume consistency ────────────────────────────────────────────────────

    private void addResumeConsistency(Document doc, JsonNode assessment) throws DocumentException {
        if (assessment == null || !assessment.has("resumeConsistency")) return;
        JsonNode rc = assessment.get("resumeConsistency");
        doc.add(sectionTitle("Resume vs. Demonstrated Skills"));

        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[]{1f, 1f});
        grid.setSpacingAfter(12);
        grid.addCell(bulletCard("Demonstrated in Interview", stringList(rc, "demonstrated"), SUCCESS_BG, SUCCESS_BORDER, SUCCESS));
        grid.addCell(bulletCard("Claimed but Not Demonstrated", stringList(rc, "notDemonstrated"), GAP_BG, GAP_BORDER, GAP));
        doc.add(grid);

        List<String> flags = stringList(rc, "flags");
        if (!flags.isEmpty()) {
            PdfPTable flagBox = new PdfPTable(1);
            flagBox.setWidthPercentage(100);
            flagBox.setSpacingAfter(12);
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(DANGER_BG);
            cell.setBorderColor(new Color(250, 200, 200));
            cell.setBorderWidth(0.75f);
            cell.setPadding(12);
            Paragraph titleP = new Paragraph("FLAGS FOR MENTOR",
                new Font(Font.HELVETICA, 7, Font.BOLD, DANGER));
            titleP.setSpacingAfter(6);
            cell.addElement(titleP);
            for (String f : flags) {
                Paragraph p = new Paragraph("•  " + f, new Font(Font.HELVETICA, 9, Font.NORMAL, DANGER));
                p.setLeading(14);
                p.setSpacingAfter(3);
                cell.addElement(p);
            }
            flagBox.addCell(cell);
            doc.add(flagBox);
        }
    }

    // ── Speech analytics ──────────────────────────────────────────────────────

    private void addSpeechAnalytics(Document doc, JsonNode assessment) throws DocumentException {
        if (assessment == null || !assessment.has("speechAnalytics")) return;
        JsonNode sa = assessment.get("speechAnalytics");
        if (sa == null || sa.isNull()) return;

        doc.add(sectionTitle("Communication Analytics"));

        PdfPTable strip = new PdfPTable(4);
        strip.setWidthPercentage(100);
        strip.setSpacingAfter(12);

        addMetricCard(strip, "Words / min", nodeInt(sa, "wpm"));
        addMetricCard(strip, "Filler words", nodeInt(sa, "fillers"));
        addMetricCard(strip, "Long silences", nodeInt(sa, "longSilences"));
        addMetricCard(strip, "Candidate turns", nodeInt(sa, "candidateTurns"));
        doc.add(strip);
    }

    private void addMetricCard(PdfPTable table, String label, int value) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT_BG);
        cell.setBorderColor(CARD_BORDER);
        cell.setBorderWidth(0.75f);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph lbl = new Paragraph(label.toUpperCase(), new Font(Font.HELVETICA, 7, Font.BOLD, MUTED));
        lbl.setAlignment(Element.ALIGN_CENTER);
        lbl.setSpacingAfter(5);
        cell.addElement(lbl);
        Paragraph val = new Paragraph(String.valueOf(value),
            new Font(Font.HELVETICA, 20, Font.BOLD, TEAL));
        val.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(val);
        table.addCell(cell);
    }

    // ── Code submissions ──────────────────────────────────────────────────────

    private void addCodeSection(Document doc, List<CodeSub> subs) throws DocumentException {
        doc.add(sectionTitle("Code Assessment"));
        for (CodeSub sub : subs) {
            PdfPTable card = new PdfPTable(1);
            card.setWidthPercentage(100);
            card.setSpacingAfter(10);
            card.setKeepTogether(true);

            PdfPCell header = new PdfPCell();
            header.setBackgroundColor(TEAL);
            header.setBorder(Rectangle.NO_BORDER);
            header.setPadding(10);
            Paragraph h = new Paragraph(
                sub.language().toUpperCase() + "  ·  Score: " + sub.score() + "/5  ·  " + cap(sub.correctness()),
                new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE));
            header.addElement(h);
            card.addCell(header);

            PdfPCell body = new PdfPCell();
            body.setBackgroundColor(CARD_BG);
            body.setBorderColor(CARD_BORDER);
            body.setBorderWidth(0.75f);
            body.setBorderWidthTop(0f);
            body.setPadding(12);

            if (sub.question() != null && !sub.question().isBlank()) {
                Paragraph q = new Paragraph("Problem: " + sub.question(),
                    new Font(Font.HELVETICA, 9, Font.ITALIC, MUTED));
                q.setSpacingAfter(6);
                body.addElement(q);
            }
            if (sub.code() != null && !sub.code().isBlank()) {
                String snippet = sub.code().length() > 600 ? sub.code().substring(0, 600) + "\n…" : sub.code();
                Paragraph code = new Paragraph(snippet,
                    new Font(Font.COURIER, 8, Font.NORMAL, new Color(30, 30, 30)));
                code.setLeading(12);
                body.addElement(code);
            }
            if (sub.feedback() != null && !sub.feedback().isBlank()) {
                Paragraph fb = new Paragraph("Feedback: " + sub.feedback(),
                    new Font(Font.HELVETICA, 9, Font.NORMAL, HEADER_DARK));
                fb.setSpacingBefore(8);
                fb.setLeading(14);
                body.addElement(fb);
            }
            card.addCell(body);
            doc.add(card);
        }
    }

    // ── Roadmap ───────────────────────────────────────────────────────────────

    private void addRoadmap(Document doc, JsonNode assessment) throws DocumentException {
        if (assessment == null) return;
        JsonNode cf = assessment.get("candidateFeedback");
        if (cf == null || !cf.has("roadmap") || !cf.get("roadmap").isArray()) return;
        JsonNode roadmap = cf.get("roadmap");
        if (roadmap.isEmpty()) return;

        doc.add(sectionTitle("Suggested Learning Roadmap"));

        for (JsonNode item : roadmap) {
            PdfPTable row = new PdfPTable(new float[]{0.15f, 0.85f});
            row.setWidthPercentage(100);
            row.setSpacingAfter(6);
            row.setKeepTogether(true);

            // Day badge
            PdfPCell dayCell = new PdfPCell();
            dayCell.setBackgroundColor(TEAL);
            dayCell.setBorder(Rectangle.NO_BORDER);
            dayCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            dayCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            dayCell.setPadding(8);
            String day = textNodeFrom(item, "day");
            Paragraph dayP = new Paragraph(day.replaceAll("[^0-9]", "").isEmpty() ? day : "Day " + day.replaceAll("[^0-9]", ""),
                new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE));
            dayP.setAlignment(Element.ALIGN_CENTER);
            dayCell.addElement(dayP);
            row.addCell(dayCell);

            // Content
            PdfPCell content = new PdfPCell();
            content.setBackgroundColor(LIGHT_BG);
            content.setBorderColor(CARD_BORDER);
            content.setBorderWidth(0.75f);
            content.setBorderWidthLeft(0f);
            content.setPadding(10);
            content.setPaddingLeft(12);

            String focus = textNodeFrom(item, "focus");
            if (!focus.isBlank()) {
                content.addElement(new Paragraph(focus, new Font(Font.HELVETICA, 10, Font.BOLD, HEADER_DARK)));
            }
            String resource = textNodeFrom(item, "resource");
            if (!resource.isBlank()) {
                Paragraph rp = new Paragraph(resource, F_BODY_SM);
                rp.setSpacingBefore(3);
                content.addElement(rp);
            }
            String why = textNodeFrom(item, "whyItMatters");
            if (!why.isBlank()) {
                Paragraph wp = new Paragraph("Why: " + why, F_SMALL);
                wp.setSpacingBefore(2);
                content.addElement(wp);
            }
            row.addCell(content);
            doc.add(row);
        }
    }

    // ── Closing ───────────────────────────────────────────────────────────────

    private void addClosing(Document doc, MentorReportContext ctx) throws DocumentException {
        Paragraph closing = new Paragraph(
            "Generated for " + ctx.candidateName() + "  ·  Bench Readiness Platform  ·  " +
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.now()),
            F_FOOTER);
        closing.setAlignment(Element.ALIGN_CENTER);
        closing.setSpacingBefore(20);
        doc.add(closing);
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private JsonNode parseAssessment(String transcriptJson) {
        if (transcriptJson == null || transcriptJson.isBlank()) return null;
        try {
            JsonNode doc = objectMapper.readTree(transcriptJson);
            JsonNode meta = doc.get("meta");
            if (meta != null) {
                JsonNode assessment = meta.get("assessment");
                if (assessment != null && !assessment.isNull()) {
                    if (assessment.isTextual()) {
                        return objectMapper.readTree(assessment.asText());
                    }
                    return assessment;
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse assessment from transcript: {}", e.getMessage());
        }
        return null;
    }

    private List<ScoreRow> parseScores(JsonNode assessment) {
        List<ScoreRow> list = new ArrayList<>();
        if (assessment == null || !assessment.has("scores")) return list;
        JsonNode scores = assessment.get("scores");
        if (!scores.isArray()) return list;
        int max = assessment.has("scoreMax") ? assessment.get("scoreMax").asInt(10) : 10;
        for (JsonNode s : scores) {
            list.add(new ScoreRow(
                textNodeFrom(s, "dimension"),
                s.has("value") ? s.get("value").asInt() : 0,
                max,
                textNodeFrom(s, "rationale"),
                textNodeFrom(s, "evidence"),
                textNodeFrom(s, "gap"),
                textNodeFrom(s, "confidence")
            ));
        }
        return list;
    }

    private List<CodeSub> parseCodeSubmissions(String transcriptJson) {
        List<CodeSub> list = new ArrayList<>();
        if (transcriptJson == null || transcriptJson.isBlank()) return list;
        try {
            JsonNode doc = objectMapper.readTree(transcriptJson);
            JsonNode meta = doc.get("meta");
            if (meta == null) return list;
            JsonNode subs = meta.has("codeSubmissions") ? meta.get("codeSubmissions")
                : (meta.has("codeSubmission") ? meta.get("codeSubmission") : null);
            if (subs == null) return list;
            if (!subs.isArray()) subs = objectMapper.createArrayNode().add(subs);
            for (JsonNode sub : subs) {
                if (sub == null || !sub.has("code")) continue;
                JsonNode aiReview = sub.get("aiReview");
                list.add(new CodeSub(
                    textNodeFrom(sub, "language"),
                    textNodeFrom(sub, "question"),
                    textNodeFrom(sub, "code"),
                    aiReview != null ? textNodeFrom(aiReview, "correctness") : "",
                    aiReview != null && aiReview.has("score") ? aiReview.get("score").asInt() : 0,
                    aiReview != null ? textNodeFrom(aiReview, "overallFeedback") : ""
                ));
            }
        } catch (Exception e) {
            log.debug("Could not parse code submissions: {}", e.getMessage());
        }
        return list;
    }

    private String signOffNote(String transcriptJson) {
        if (transcriptJson == null || transcriptJson.isBlank()) return null;
        try {
            JsonNode doc = objectMapper.readTree(transcriptJson);
            JsonNode meta = doc.get("meta");
            if (meta != null && meta.has("signOffNote")) return meta.get("signOffNote").asText();
        } catch (Exception ignored) {}
        return null;
    }

    private String candidateName(Interview interview) {
        try {
            if (interview.getTranscriptJson() != null) {
                JsonNode doc = objectMapper.readTree(interview.getTranscriptJson());
                JsonNode meta = doc.get("meta");
                if (meta != null && meta.has("candidateName")) return meta.get("candidateName").asText();
            }
        } catch (Exception ignored) {}
        return "Candidate";
    }

    private String candidateEmail(Interview interview) {
        try {
            if (interview.getTranscriptJson() != null) {
                JsonNode doc = objectMapper.readTree(interview.getTranscriptJson());
                JsonNode meta = doc.get("meta");
                if (meta != null && meta.has("candidateEmail")) return meta.get("candidateEmail").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String jdTitle(Interview interview) {
        try {
            if (interview.getTranscriptJson() != null) {
                JsonNode doc = objectMapper.readTree(interview.getTranscriptJson());
                JsonNode meta = doc.get("meta");
                if (meta != null && meta.has("jdTitle")) return meta.get("jdTitle").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String textNode(JsonNode node, String field) {
        if (node == null || !node.has(field)) return "";
        return node.get(field).asText("").trim();
    }

    private String textNodeFrom(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field)) return "";
        return node.get(field).asText("").trim();
    }

    private int nodeInt(JsonNode node, String field) {
        if (node == null || !node.has(field)) return 0;
        return node.get(field).asInt(0);
    }

    private List<String> stringList(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        if (node == null || !node.has(field)) return result;
        JsonNode arr = node.get(field);
        if (arr.isArray()) {
            for (JsonNode el : arr) {
                String s = el.asText("").trim();
                if (!s.isBlank()) result.add(s);
            }
        } else if (arr.isTextual() && !arr.asText().isBlank()) {
            result.add(arr.asText().trim());
        }
        return result;
    }

    private String safe(String v) { return v != null && !v.isBlank() ? v : "—"; }
    private String cap(String v) {
        if (v == null || v.isBlank()) return "";
        return Character.toUpperCase(v.charAt(0)) + v.substring(1).toLowerCase();
    }

    private Color scoreColor(int value, int max) {
        double pct = max > 0 ? (double) value / max : 0;
        if (pct >= 0.7) return SUCCESS;
        if (pct >= 0.5) return new Color(180, 120, 10);
        return DANGER;
    }

    // ── Section title ─────────────────────────────────────────────────────────

    private PdfPTable sectionTitle(String title) throws DocumentException {
        PdfPTable bar = new PdfPTable(new float[]{0.012f, 0.988f});
        bar.setWidthPercentage(100);
        bar.setSpacingBefore(14);
        bar.setSpacingAfter(10);
        PdfPCell accent = new PdfPCell();
        accent.setBackgroundColor(TEAL);
        accent.setBorder(Rectangle.NO_BORDER);
        accent.setFixedHeight(22);
        bar.addCell(accent);
        PdfPCell label = new PdfPCell(new Phrase("  " + title.toUpperCase(), F_SECTION));
        label.setBorder(Rectangle.NO_BORDER);
        label.setBackgroundColor(LIGHT_BG);
        label.setVerticalAlignment(Element.ALIGN_MIDDLE);
        label.setPaddingLeft(10);
        label.setPaddingTop(5);
        label.setPaddingBottom(5);
        bar.addCell(label);
        return bar;
    }

    // ── Page header/footer ────────────────────────────────────────────────────

    private static final class MentorPageEvent extends PdfPageEventHelper {
        private final String candidateName;
        private PdfTemplate totalPages;
        private BaseFont baseFont;

        MentorPageEvent(String candidateName) { this.candidateName = candidateName; }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPages = writer.getDirectContent().createTemplate(24, 14);
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) { baseFont = null; }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (baseFont == null) return;
            PdfContentByte canvas = writer.getDirectContent();
            float left = document.leftMargin();
            float right = document.getPageSize().getWidth() - document.rightMargin();
            int page = writer.getPageNumber();

            if (page > 1) {
                float top = document.getPageSize().getTop() - 26;
                canvas.setColorFill(TEAL_LIGHT);
                canvas.rectangle(left, top, right - left, 2f);
                canvas.fill();
                canvas.beginText();
                canvas.setFontAndSize(baseFont, 8);
                canvas.setColorFill(HEADER_DARK);
                canvas.showTextAligned(Element.ALIGN_LEFT, "Bench Readiness  |  Mentor Report", left, top - 12, 0);
                canvas.setColorFill(MUTED);
                canvas.showTextAligned(Element.ALIGN_RIGHT, candidateName, right, top - 12, 0);
                canvas.endText();
            }

            float lineY = document.bottomMargin() - 10;
            canvas.setColorFill(new Color(200, 235, 240));
            canvas.rectangle(left, lineY + 8, right - left, 0.5f);
            canvas.fill();
            canvas.beginText();
            canvas.setFontAndSize(baseFont, 7);
            canvas.setColorFill(MUTED);
            canvas.showTextAligned(Element.ALIGN_LEFT, "Confidential — For Mentor Use Only", left, lineY, 0);
            String pageText = "Page " + page + " of ";
            float w = baseFont.getWidthPoint(pageText, 7);
            canvas.showTextAligned(Element.ALIGN_RIGHT, pageText, right - w, lineY, 0);
            canvas.endText();
            canvas.addTemplate(totalPages, right - w + 1, lineY);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            if (baseFont == null || totalPages == null) return;
            totalPages.beginText();
            totalPages.setFontAndSize(baseFont, 7);
            totalPages.setTextMatrix(0, 0);
            totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPages.endText();
        }
    }
}
