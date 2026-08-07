package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.ComplianceServiceClient;
import com.benchreadiness.interview.entity.Engineer;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.JobDescription;
import com.benchreadiness.interview.repository.EngineerRepository;
import com.benchreadiness.interview.repository.InterviewRepository;
import com.benchreadiness.interview.repository.JobDescriptionRepository;
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
    private final EngineerRepository engineerRepository;
    private final JobDescriptionRepository jdRepository;
    private final ComplianceServiceClient complianceServiceClient;
    private final InterviewQuestionService interviewQuestionService;
    private final ObjectMapper objectMapper;

    public MentorReportPdfService(InterviewRepository interviewRepository,
                                   EngineerRepository engineerRepository,
                                   JobDescriptionRepository jdRepository,
                                   ComplianceServiceClient complianceServiceClient,
                                   InterviewQuestionService interviewQuestionService,
                                   ObjectMapper objectMapper) {
        this.interviewRepository = interviewRepository;
        this.engineerRepository = engineerRepository;
        this.jdRepository = jdRepository;
        this.complianceServiceClient = complianceServiceClient;
        this.interviewQuestionService = interviewQuestionService;
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
    private static final Color QA_BG         = new Color(248, 250, 252);
    private static final Color QA_BORDER     = new Color(210, 230, 245);

    private static final Font F_BRAND     = new Font(Font.HELVETICA, 7,  Font.BOLD,   ACCENT_AMBER);
    private static final Font F_SUBTITLE  = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(180, 225, 235));
    private static final Font F_TITLE     = new Font(Font.HELVETICA, 24, Font.BOLD,   Color.WHITE);
    private static final Font F_CHIP_BOLD = new Font(Font.HELVETICA, 9,  Font.BOLD,   Color.WHITE);
    private static final Font F_SECTION   = new Font(Font.HELVETICA, 11, Font.BOLD,   HEADER_DARK);
    private static final Font F_BODY      = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(28, 35, 42));
    private static final Font F_BODY_SM   = new Font(Font.HELVETICA, 9,  Font.NORMAL, new Color(50, 58, 68));
    private static final Font F_SMALL     = new Font(Font.HELVETICA, 8,  Font.NORMAL, MUTED);
    private static final Font F_FOOTER    = new Font(Font.HELVETICA, 7,  Font.NORMAL, MUTED);

    // ── Data model ────────────────────────────────────────────────────────────

    public record MentorReportContext(
        String candidateName,
        String candidateEmail,
        String jdTitle,
        String interviewDate,
        String finalVerdict,
        String proposedVerdict,
        String signOffNote,
        String overallSummary,
        List<String> topStrengths,
        List<String> topGaps,
        List<ScoreRow> scores,
        List<String> strengths,
        List<String> areasToImprove,
        List<ProCon> prosAndCons,
        BehavioralSignals behavioralSignals,
        ResumeConsistency resumeConsistency,
        InterviewQuality interviewQuality,
        SpeechAnalytics speechAnalytics,
        IntegritySummary integrity,
        List<RoadmapItem> roadmap,
        List<QaEntry> qaEntries,
        List<CodeSub> codeSubmissions
    ) {}

    public record ScoreRow(String dimension, int value, int max, String rationale, String evidence, String gap, String confidence) {}
    public record ProCon(String category, List<String> pros, List<String> cons) {}
    public record BehavioralSignals(String ownership, String learningAgility, String communication, String confidence, String summary) {}
    public record ResumeConsistency(List<String> claimed, List<String> demonstrated, List<String> notDemonstrated, int score, List<String> flags) {}
    public record InterviewQuality(int coverageScore, List<String> covered, List<String> missed, String note) {}
    public record SpeechAnalytics(int wpm, int fillers, int longSilences, int wordCount, int candidateTurns) {}
    public record IntegritySummary(Integer proctoringScore, int tabSwitchCount, boolean tabSwitchViolation, int fullscreenExitCount, String status) {}
    public record RoadmapItem(String day, String category, String gap, String focus, String whyItMatters, String resource, String exercise, int estimatedHours) {}
    public record QaEntry(int slot, String question, String answer, String questionType) {}
    public record CodeSub(String language, String question, String code, String correctness, int score,
                           String feedback, String timeComplexity, String spaceComplexity, String candidateComplexity,
                           List<String> bugs, List<String> improvements, List<TestResult> testResults) {}
    public record TestResult(String name, boolean passed) {}

    // ── Public API ────────────────────────────────────────────────────────────

    public MentorReportContext buildContext(String interviewId, String userId) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));

        // Primary source: transcriptJson.meta.aiAssessment — this is exactly what the
        // staff review page renders, so the mentor report must read from the same place
        // rather than a possibly-stale/out-of-sync copy in the compliance service.
        JsonNode assessment = metaNode(interview.getTranscriptJson(), "aiAssessment");
        if (assessment == null || assessment.isMissingNode() || assessment.isNull()) {
            try {
                Map<String, Object> assessmentData = complianceServiceClient.getAssessmentResponse(interviewId, userId);
                if (assessmentData != null && assessmentData.containsKey("assessmentJson")) {
                    String assessmentJson = (String) assessmentData.get("assessmentJson");
                    assessment = objectMapper.readTree(assessmentJson);
                }
            } catch (Exception e) {
                log.warn("Could not load fallback assessment for interview {}: {}", interviewId, e.getMessage());
            }
        }

        // Q&A from slot questions (persisted per-slot record, same source as the review page)
        List<QaEntry> qa = new ArrayList<>();
        try {
            interviewQuestionService.listByInterview(interviewId).forEach(q -> {
                String answer = q.getCandidateAnswer() != null && !q.getCandidateAnswer().isBlank()
                    ? q.getCandidateAnswer() : "[No answer recorded]";
                qa.add(new QaEntry(q.getSlotNumber(), q.getQuestionText(), answer, q.getQuestionType()));
            });
        } catch (Exception e) {
            log.warn("Could not load questions for interview {}: {}", interviewId, e.getMessage());
        }

        List<CodeSub> codeSubs = parseCodeSubmissions(interview.getTranscriptJson());

        String interviewDate = interview.getCreatedAt() != null
            ? DateTimeFormatter.ofPattern("d MMM yyyy")
                .withZone(ZoneId.systemDefault())
                .format(interview.getCreatedAt())
            : null;

        // Candidate/JD identity comes from the engineer + JD records (same as the client brief),
        // not from transcript metadata which does not carry these fields.
        Engineer engineer = interview.getEngineerId() != null
            ? engineerRepository.findById(interview.getEngineerId()).orElse(null) : null;
        JobDescription jd = interview.getJdId() != null
            ? jdRepository.findById(interview.getJdId()).orElse(null) : null;
        String candidateName = engineer != null && engineer.getName() != null ? engineer.getName() : "Candidate";
        String candidateEmail = engineer != null ? engineer.getEmail() : null;
        String jdTitle = jd != null ? jd.getTitle() : "Position";

        return new MentorReportContext(
            safe(candidateName),
            safe(candidateEmail),
            safe(jdTitle),
            interviewDate != null ? interviewDate : "—",
            interview.getFinalVerdict() != null ? interview.getFinalVerdict().name() : null,
            interview.getProposedVerdict() != null ? interview.getProposedVerdict().name() : null,
            interview.getSignOffNote(),
            assessment != null ? assessment.path("summary").asText("") : "",
            stringList(assessment, "strengths"),
            stringList(assessment, "gaps"),
            parseScores(assessment),
            parseStringList(assessment, "strengths"),
            parseStringList(assessment, "areasToImprove"),
            parseProsAndCons(assessment),
            parseBehavioralSignals(assessment),
            parseResumeConsistency(assessment),
            parseInterviewQuality(assessment),
            parseSpeechAnalytics(assessment),
            parseIntegrity(interview),
            parseRoadmap(assessment),
            qa,
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
            if (!ctx.topStrengths().isEmpty() || !ctx.topGaps().isEmpty()) {
                addTopLevelStrengthsGaps(doc, ctx.topStrengths(), ctx.topGaps());
            }
            if (!ctx.scores().isEmpty()) addScoreGrid(doc, ctx.scores());
            addStrengthsGaps(doc, ctx.strengths(), ctx.areasToImprove(), ctx.prosAndCons());
            addBehavioralSignals(doc, ctx.behavioralSignals());
            addResumeConsistency(doc, ctx.resumeConsistency());
            addInterviewQuality(doc, ctx.interviewQuality());
            addSpeechAnalytics(doc, ctx.speechAnalytics());
            addIntegritySummary(doc, ctx.integrity());
            if (!ctx.qaEntries().isEmpty()) addQaSection(doc, ctx.qaEntries());
            if (!ctx.codeSubmissions().isEmpty()) addCodeSection(doc, ctx.codeSubmissions());
            if (!ctx.roadmap().isEmpty()) addRoadmap(doc, ctx.roadmap());
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
        if (value == null || value.isBlank() || value.equals("—")) {
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
        Color bg; Color border; Color fg; String icon;
        if (verdict == null) verdict = "PENDING";
        switch (verdict) {
            case "READY"              -> { bg = SUCCESS_BG; border = SUCCESS_BORDER; fg = SUCCESS; icon = "✔  READY FOR PLACEMENT"; }
            case "NEEDS_1_WEEK_PREP"  -> { bg = GAP_BG; border = GAP_BORDER; fg = GAP; icon = "⚡  NEEDS 1-WEEK PREPARATION"; }
            case "NEEDS_RESKILLING"   -> { bg = DANGER_BG; border = new Color(250, 200, 200); fg = DANGER; icon = "⚠  NEEDS RESKILLING"; }
            default                   -> { bg = new Color(240, 245, 255); border = new Color(200, 215, 245); fg = new Color(50, 80, 160); icon = "◉  " + verdict.replace("_", " "); }
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

        if (ctx.overallSummary() != null && !ctx.overallSummary().isBlank()) {
            PdfPTable sumBox = new PdfPTable(1);
            sumBox.setWidthPercentage(100);
            sumBox.setSpacingAfter(12);
            PdfPCell sumCell = new PdfPCell();
            sumCell.setBackgroundColor(CARD_BG);
            sumCell.setBorderColor(CARD_BORDER);
            sumCell.setBorderWidth(0.75f);
            sumCell.setPadding(14);
            Paragraph sumP = new Paragraph(ctx.overallSummary(), F_BODY);
            sumP.setLeading(16);
            sumCell.addElement(sumP);
            sumBox.addCell(sumCell);
            doc.add(sumBox);
        }
    }

    // ── Score grid ────────────────────────────────────────────────────────────

    private void addScoreGrid(Document doc, List<ScoreRow> scores) throws DocumentException {
        doc.add(sectionTitle("Skill Scores"));

        for (ScoreRow s : scores) {
            PdfPTable row = new PdfPTable(new float[]{0.22f, 0.78f});
            row.setWidthPercentage(100);
            row.setSpacingAfter(8);
            row.setKeepTogether(true);

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

    // ── Top-level AI assessment summary (matches the "AI Assessment" panel on the review page) ──

    private void addTopLevelStrengthsGaps(Document doc, List<String> strengths, List<String> gaps) throws DocumentException {
        doc.add(sectionTitle("AI Assessment — Strengths & Gaps vs JD"));
        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[]{1f, 1f});
        grid.setSpacingAfter(12);
        grid.addCell(bulletCard("Strengths", strengths.isEmpty() ? List.of("—") : strengths, SUCCESS_BG, SUCCESS_BORDER, SUCCESS));
        grid.addCell(bulletCard("Gaps vs JD", gaps.isEmpty() ? List.of("—") : gaps, GAP_BG, GAP_BORDER, GAP));
        doc.add(grid);
    }

    // ── Interview Quality ─────────────────────────────────────────────────────

    private void addInterviewQuality(Document doc, InterviewQuality iq) throws DocumentException {
        if (iq == null) return;
        doc.add(sectionTitle("Interview Quality / Coverage"));

        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        header.setSpacingAfter(8);
        PdfPCell scoreCell = new PdfPCell();
        scoreCell.setBackgroundColor(LIGHT_BG);
        scoreCell.setBorderColor(CARD_BORDER);
        scoreCell.setBorderWidth(0.75f);
        scoreCell.setPadding(10);
        scoreCell.addElement(new Paragraph("Coverage score: " + iq.coverageScore() + "/10",
            new Font(Font.HELVETICA, 10, Font.BOLD, TEAL)));
        header.addCell(scoreCell);
        doc.add(header);

        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[]{1f, 1f});
        grid.setSpacingAfter(8);
        grid.addCell(bulletCard("Covered", iq.covered().isEmpty() ? List.of("—") : iq.covered(), SUCCESS_BG, SUCCESS_BORDER, SUCCESS));
        grid.addCell(bulletCard("Not reached (time-limited)", iq.missed().isEmpty() ? List.of("None — full coverage") : iq.missed(),
            new Color(240, 245, 255), new Color(200, 215, 245), new Color(50, 80, 160)));
        doc.add(grid);

        if (iq.note() != null && !iq.note().isBlank()) {
            Paragraph note = new Paragraph(iq.note(), new Font(Font.HELVETICA, 8, Font.ITALIC, MUTED));
            note.setSpacingAfter(12);
            doc.add(note);
        }
    }

    // ── Integrity / proctoring summary ───────────────────────────────────────

    private void addIntegritySummary(Document doc, IntegritySummary integrity) throws DocumentException {
        if (integrity == null) return;
        boolean hasSignal = integrity.proctoringScore() != null || integrity.tabSwitchCount() > 0
            || integrity.fullscreenExitCount() > 0;
        if (!hasSignal) return;

        doc.add(sectionTitle("Interview Integrity"));
        PdfPTable strip = new PdfPTable(3);
        strip.setWidthPercentage(100);
        strip.setSpacingAfter(12);
        if (integrity.proctoringScore() != null) {
            addMetricCard(strip, "Integrity score", integrity.proctoringScore());
        } else {
            PdfPCell empty = new PdfPCell(new Phrase(" "));
            empty.setBorder(Rectangle.NO_BORDER);
            strip.addCell(empty);
        }
        addMetricCard(strip, "Tab switches", integrity.tabSwitchCount());
        addMetricCard(strip, "Fullscreen exits", integrity.fullscreenExitCount());
        doc.add(strip);

        if (integrity.tabSwitchViolation()) {
            PdfPTable flagBox = new PdfPTable(1);
            flagBox.setWidthPercentage(100);
            flagBox.setSpacingAfter(12);
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(DANGER_BG);
            cell.setBorderColor(new Color(250, 200, 200));
            cell.setBorderWidth(0.75f);
            cell.setPadding(10);
            cell.addElement(new Paragraph("⚠  Tab-switch violation flagged during this interview.",
                new Font(Font.HELVETICA, 9, Font.BOLD, DANGER)));
            flagBox.addCell(cell);
            doc.add(flagBox);
        }
    }

    // ── Strengths & Gaps ──────────────────────────────────────────────────────

    private void addStrengthsGaps(Document doc, List<String> strengths, List<String> areasToImprove,
                                   List<ProCon> prosAndCons) throws DocumentException {
        // Flatten all pros/cons from prosAndCons if direct strength/areas lists are thin
        if (strengths.isEmpty() && !prosAndCons.isEmpty()) {
            strengths = new ArrayList<>();
            for (ProCon pc : prosAndCons) strengths.addAll(pc.pros());
        }
        if (areasToImprove.isEmpty() && !prosAndCons.isEmpty()) {
            areasToImprove = new ArrayList<>();
            for (ProCon pc : prosAndCons) areasToImprove.addAll(pc.cons());
        }
        if (strengths.isEmpty() && areasToImprove.isEmpty()) return;

        doc.add(sectionTitle("Strengths & Growth Areas"));

        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[]{1f, 1f});
        grid.setSpacingAfter(12);
        grid.addCell(bulletCard("Demonstrated Strengths",
            strengths.isEmpty() ? List.of("See individual skill scores above.") : strengths,
            SUCCESS_BG, SUCCESS_BORDER, SUCCESS));
        grid.addCell(bulletCard("Priority Growth Areas",
            areasToImprove.isEmpty() ? List.of("No specific gaps identified.") : areasToImprove,
            GAP_BG, GAP_BORDER, GAP));
        doc.add(grid);

        // Per-category pros/cons detail
        if (!prosAndCons.isEmpty()) {
            for (ProCon pc : prosAndCons) {
                if (pc.pros().isEmpty() && pc.cons().isEmpty()) continue;
                PdfPTable pcCard = new PdfPTable(new float[]{0.012f, 0.988f});
                pcCard.setWidthPercentage(100);
                pcCard.setSpacingAfter(6);
                pcCard.setKeepTogether(true);
                PdfPCell accentBar = new PdfPCell();
                accentBar.setBackgroundColor(TEAL);
                accentBar.setBorder(Rectangle.NO_BORDER);
                accentBar.setFixedHeight(1f);
                pcCard.addCell(accentBar);
                PdfPCell body = new PdfPCell();
                body.setBackgroundColor(LIGHT_BG);
                body.setBorderColor(CARD_BORDER);
                body.setBorderWidth(0.75f);
                body.setBorderWidthLeft(0f);
                body.setPadding(10);
                Paragraph catLabel = new Paragraph(pc.category(),
                    new Font(Font.HELVETICA, 9, Font.BOLD, HEADER_DARK));
                catLabel.setSpacingAfter(5);
                body.addElement(catLabel);
                for (String pro : pc.pros()) {
                    Paragraph p = new Paragraph("✓  " + pro, new Font(Font.HELVETICA, 8, Font.NORMAL, SUCCESS));
                    p.setLeading(14);
                    body.addElement(p);
                }
                for (String con : pc.cons()) {
                    Paragraph p = new Paragraph("→  " + con, new Font(Font.HELVETICA, 8, Font.NORMAL, GAP));
                    p.setLeading(14);
                    body.addElement(p);
                }
                pcCard.addCell(body);
                doc.add(pcCard);
            }
        }
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

    private void addBehavioralSignals(Document doc, BehavioralSignals bs) throws DocumentException {
        if (bs == null) return;
        doc.add(sectionTitle("Behavioral Signals"));

        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[]{1f, 1f});
        grid.setSpacingAfter(12);

        String[][] signals = {
            {"Ownership", bs.ownership()},
            {"Learning Agility", bs.learningAgility()},
            {"Communication", bs.communication()},
            {"Confidence Calibration", bs.confidence()},
        };

        int cellCount = 0;
        for (String[] sig : signals) {
            if (sig[1] == null || sig[1].isBlank()) continue;
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
            cell.addElement(new Paragraph(cap(sig[1]),
                new Font(Font.HELVETICA, 11, Font.BOLD, levelColor)));
            grid.addCell(cell);
            cellCount++;
        }
        if (cellCount % 2 != 0) {
            PdfPCell empty = new PdfPCell(new Phrase(" "));
            empty.setBorder(Rectangle.NO_BORDER);
            grid.addCell(empty);
        }
        doc.add(grid);

        if (bs.summary() != null && !bs.summary().isBlank()) {
            PdfPTable box = new PdfPTable(1);
            box.setWidthPercentage(100);
            box.setSpacingAfter(12);
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(CARD_BG);
            cell.setBorderColor(CARD_BORDER);
            cell.setBorderWidth(0.75f);
            cell.setPadding(12);
            Paragraph p = new Paragraph(bs.summary(), F_BODY_SM);
            p.setLeading(15);
            cell.addElement(p);
            box.addCell(cell);
            doc.add(box);
        }
    }

    // ── Resume consistency ────────────────────────────────────────────────────

    private void addResumeConsistency(Document doc, ResumeConsistency rc) throws DocumentException {
        if (rc == null) return;
        doc.add(sectionTitle("Resume vs. Demonstrated Skills"));

        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[]{1f, 1f});
        grid.setSpacingAfter(12);
        grid.addCell(bulletCard("Demonstrated in Interview",
            rc.demonstrated().isEmpty() ? List.of("—") : rc.demonstrated(), SUCCESS_BG, SUCCESS_BORDER, SUCCESS));
        grid.addCell(bulletCard("Claimed but Not Demonstrated",
            rc.notDemonstrated().isEmpty() ? List.of("—") : rc.notDemonstrated(), GAP_BG, GAP_BORDER, GAP));
        doc.add(grid);

        if (!rc.flags().isEmpty()) {
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
            for (String f : rc.flags()) {
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

    private void addSpeechAnalytics(Document doc, SpeechAnalytics sa) throws DocumentException {
        if (sa == null || (sa.wpm() == 0 && sa.fillers() == 0 && sa.candidateTurns() == 0)) return;
        doc.add(sectionTitle("Communication Analytics"));

        PdfPTable strip = new PdfPTable(4);
        strip.setWidthPercentage(100);
        strip.setSpacingAfter(12);
        addMetricCard(strip, "Words / min", sa.wpm());
        addMetricCard(strip, "Filler words", sa.fillers());
        addMetricCard(strip, "Long silences", sa.longSilences());
        addMetricCard(strip, "Candidate turns", sa.candidateTurns());
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

    // ── Q&A section ───────────────────────────────────────────────────────────

    private void addQaSection(Document doc, List<QaEntry> qa) throws DocumentException {
        doc.add(sectionTitle("Interview Q&A"));
        for (QaEntry entry : qa) {
            PdfPTable card = new PdfPTable(1);
            card.setWidthPercentage(100);
            card.setSpacingAfter(8);
            card.setKeepTogether(true);

            PdfPCell qCell = new PdfPCell();
            qCell.setBackgroundColor(new Color(230, 245, 250));
            qCell.setBorderColor(CARD_BORDER);
            qCell.setBorderWidth(0.75f);
            qCell.setPadding(10);
            String qPrefix = "Q" + entry.slot()
                + (entry.questionType() != null && !entry.questionType().isBlank() ? " (" + cap(entry.questionType()) + ")" : "")
                + ":  ";
            Paragraph qLabel = new Paragraph(qPrefix + entry.question(),
                new Font(Font.HELVETICA, 9, Font.BOLD, HEADER_DARK));
            qLabel.setLeading(14);
            qCell.addElement(qLabel);
            card.addCell(qCell);

            PdfPCell aCell = new PdfPCell();
            aCell.setBackgroundColor(QA_BG);
            aCell.setBorderColor(QA_BORDER);
            aCell.setBorderWidth(0.75f);
            aCell.setBorderWidthTop(0f);
            aCell.setPadding(10);
            aCell.setPaddingLeft(14);
            String answerText = entry.answer();
            if (answerText.length() > 800) answerText = answerText.substring(0, 800) + "…";
            Paragraph aLabel = new Paragraph(answerText, F_BODY_SM);
            aLabel.setLeading(14);
            aCell.addElement(aLabel);
            card.addCell(aCell);
            doc.add(card);
        }
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
            header.addElement(new Paragraph(
                sub.language().toUpperCase() + "  ·  Score: " + sub.score() + "/5  ·  " + cap(sub.correctness()),
                new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE)));
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

            if (!sub.testResults().isEmpty()) {
                Paragraph testsLabel = new Paragraph("Test results:",
                    new Font(Font.HELVETICA, 8, Font.BOLD, MUTED));
                testsLabel.setSpacingBefore(8);
                body.addElement(testsLabel);
                for (TestResult t : sub.testResults()) {
                    Paragraph tp = new Paragraph((t.passed() ? "✓  " : "✗  ") + t.name(),
                        new Font(Font.HELVETICA, 8, Font.NORMAL, t.passed() ? SUCCESS : DANGER));
                    tp.setIndentationLeft(8);
                    body.addElement(tp);
                }
            }

            if (sub.timeComplexity() != null && !sub.timeComplexity().isBlank()) {
                Paragraph tc = new Paragraph(
                    "Time: " + sub.timeComplexity()
                    + (sub.spaceComplexity() != null && !sub.spaceComplexity().isBlank() ? "   Space: " + sub.spaceComplexity() : ""),
                    new Font(Font.COURIER, 8, Font.NORMAL, MUTED));
                tc.setSpacingBefore(6);
                body.addElement(tc);
            }
            if (sub.candidateComplexity() != null && !sub.candidateComplexity().isBlank()) {
                Paragraph cc = new Paragraph("Candidate stated complexity: " + sub.candidateComplexity(),
                    new Font(Font.HELVETICA, 8, Font.ITALIC, MUTED));
                cc.setSpacingBefore(2);
                body.addElement(cc);
            }

            if (!sub.bugs().isEmpty()) {
                Paragraph bugsLabel = new Paragraph("Bugs:", new Font(Font.HELVETICA, 8, Font.BOLD, DANGER));
                bugsLabel.setSpacingBefore(8);
                body.addElement(bugsLabel);
                for (String bug : sub.bugs()) {
                    Paragraph bp = new Paragraph("•  " + bug, new Font(Font.HELVETICA, 8, Font.NORMAL, DANGER));
                    bp.setLeading(13);
                    body.addElement(bp);
                }
            }
            if (!sub.improvements().isEmpty()) {
                Paragraph impLabel = new Paragraph("Suggested improvements:", new Font(Font.HELVETICA, 8, Font.BOLD, TEAL));
                impLabel.setSpacingBefore(8);
                body.addElement(impLabel);
                for (String imp : sub.improvements()) {
                    Paragraph ip = new Paragraph("•  " + imp, new Font(Font.HELVETICA, 8, Font.NORMAL, HEADER_DARK));
                    ip.setLeading(13);
                    body.addElement(ip);
                }
            }

            if (sub.feedback() != null && !sub.feedback().isBlank()) {
                Paragraph fb = new Paragraph("Overall feedback: " + sub.feedback(),
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

    private void addRoadmap(Document doc, List<RoadmapItem> roadmap) throws DocumentException {
        doc.add(sectionTitle("Suggested Learning Roadmap"));
        for (RoadmapItem item : roadmap) {
            PdfPTable row = new PdfPTable(new float[]{0.15f, 0.85f});
            row.setWidthPercentage(100);
            row.setSpacingAfter(6);
            row.setKeepTogether(true);

            PdfPCell dayCell = new PdfPCell();
            dayCell.setBackgroundColor(TEAL);
            dayCell.setBorder(Rectangle.NO_BORDER);
            dayCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            dayCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            dayCell.setPadding(8);
            String dayDisplay = item.day() != null ? (item.day().matches("\\d+") ? "Day " + item.day() : item.day()) : "Day 1";
            Paragraph dayP = new Paragraph(dayDisplay, new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE));
            dayP.setAlignment(Element.ALIGN_CENTER);
            dayCell.addElement(dayP);
            row.addCell(dayCell);

            PdfPCell content = new PdfPCell();
            content.setBackgroundColor(LIGHT_BG);
            content.setBorderColor(CARD_BORDER);
            content.setBorderWidth(0.75f);
            content.setBorderWidthLeft(0f);
            content.setPadding(10);
            content.setPaddingLeft(12);
            if (item.focus() != null && !item.focus().isBlank()) {
                content.addElement(new Paragraph(item.focus(), new Font(Font.HELVETICA, 10, Font.BOLD, HEADER_DARK)));
            }
            if (item.resource() != null && !item.resource().isBlank()) {
                Paragraph rp = new Paragraph(item.resource(), F_BODY_SM);
                rp.setSpacingBefore(3);
                content.addElement(rp);
            }
            if (item.whyItMatters() != null && !item.whyItMatters().isBlank()) {
                Paragraph wp = new Paragraph("Why: " + item.whyItMatters(), F_SMALL);
                wp.setSpacingBefore(2);
                content.addElement(wp);
            }
            if (item.exercise() != null && !item.exercise().isBlank()) {
                Paragraph ep = new Paragraph("Exercise: " + item.exercise(),
                    new Font(Font.HELVETICA, 8, Font.ITALIC, MUTED));
                ep.setSpacingBefore(2);
                content.addElement(ep);
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

    // ── Parsing helpers (use the real assessment JSON keys) ───────────────────

    @SuppressWarnings("unchecked")
    private List<ScoreRow> parseScores(JsonNode assessment) {
        List<ScoreRow> list = new ArrayList<>();
        if (assessment == null || !assessment.has("categoryScores")) return list;
        JsonNode scores = assessment.get("categoryScores");
        if (!scores.isArray()) return list;
        int max = assessment.has("scoreMax") ? assessment.get("scoreMax").asInt(10) : 10;
        for (JsonNode s : scores) {
            int val = s.has("value") ? s.get("value").asInt(0) : 0;
            if (val <= 0) continue; // skip null/uncovered categories
            list.add(new ScoreRow(
                s.path("dimension").asText(""),
                val,
                max,
                s.path("rationale").asText(""),
                s.path("evidence").asText(""),
                s.path("gap").asText(""),
                s.path("confidence").asText("medium")
            ));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(JsonNode assessment, String field) {
        List<String> result = new ArrayList<>();
        if (assessment == null) return result;
        // Try direct field first
        JsonNode node = assessment.path(field);
        if (!node.isMissingNode() && node.isArray()) {
            for (JsonNode el : node) {
                String s = el.asText("").trim();
                if (!s.isBlank()) result.add(s);
            }
        }
        // Try inside candidateFeedback
        if (result.isEmpty()) {
            JsonNode cf = assessment.path("candidateFeedback");
            if (!cf.isMissingNode()) {
                JsonNode inner = cf.path(field);
                if (inner.isArray()) {
                    for (JsonNode el : inner) {
                        String s = el.asText("").trim();
                        if (!s.isBlank()) result.add(s);
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<ProCon> parseProsAndCons(JsonNode assessment) {
        List<ProCon> result = new ArrayList<>();
        if (assessment == null) return result;
        JsonNode cf = assessment.path("candidateFeedback");
        if (cf.isMissingNode()) return result;
        JsonNode pac = cf.path("prosAndCons");
        if (!pac.isArray()) return result;
        for (JsonNode item : pac) {
            List<String> pros = new ArrayList<>();
            List<String> cons = new ArrayList<>();
            if (item.path("pros").isArray()) item.path("pros").forEach(n -> { if (!n.asText("").isBlank()) pros.add(n.asText()); });
            if (item.path("cons").isArray()) item.path("cons").forEach(n -> { if (!n.asText("").isBlank()) cons.add(n.asText()); });
            result.add(new ProCon(item.path("category").asText("General"), pros, cons));
        }
        return result;
    }

    private BehavioralSignals parseBehavioralSignals(JsonNode assessment) {
        if (assessment == null || !assessment.has("behavioralSignals")) return null;
        JsonNode bs = assessment.get("behavioralSignals");
        return new BehavioralSignals(
            bs.path("ownershipLevel").asText(bs.path("ownership").asText("")),
            bs.path("learningAgility").asText(""),
            bs.path("communicationStructure").asText(bs.path("communication").asText("")),
            bs.path("confidenceCalibration").asText(bs.path("confidence").asText("")),
            bs.path("summary").asText("")
        );
    }

    private ResumeConsistency parseResumeConsistency(JsonNode assessment) {
        if (assessment == null || !assessment.has("resumeConsistency")) return null;
        JsonNode rc = assessment.get("resumeConsistency");
        return new ResumeConsistency(
            stringList(rc, "claimed"),
            stringList(rc, "demonstrated"),
            stringList(rc, "notDemonstrated"),
            rc.path("consistencyScore").asInt(0),
            stringList(rc, "flags")
        );
    }

    private SpeechAnalytics parseSpeechAnalytics(JsonNode assessment) {
        if (assessment == null || !assessment.has("speechAnalytics")) return null;
        JsonNode sa = assessment.get("speechAnalytics");
        return new SpeechAnalytics(
            sa.path("wpm").asInt(0),
            sa.path("fillers").asInt(0),
            sa.path("longSilences").asInt(0),
            sa.path("wordCount").asInt(0),
            sa.path("candidateTurns").asInt(0)
        );
    }

    private List<RoadmapItem> parseRoadmap(JsonNode assessment) {
        List<RoadmapItem> result = new ArrayList<>();
        if (assessment == null) return result;
        JsonNode cf = assessment.path("candidateFeedback");
        if (cf.isMissingNode()) return result;
        JsonNode roadmap = cf.path("roadmap");
        if (!roadmap.isArray()) return result;
        for (JsonNode item : roadmap) {
            int dayNum = item.path("day").asInt(0);
            if (dayNum <= 0) dayNum = 1;
            result.add(new RoadmapItem(
                String.valueOf(dayNum),
                item.path("category").asText(""),
                item.path("gap").asText(""),
                item.path("focus").asText(""),
                item.path("whyItMatters").asText(""),
                item.path("resource").asText(""),
                item.path("exercise").asText(""),
                item.path("estimatedHours").asInt(2)
            ));
        }
        return result;
    }

    private List<CodeSub> parseCodeSubmissions(String transcriptJson) {
        List<CodeSub> list = new ArrayList<>();
        if (transcriptJson == null || transcriptJson.isBlank()) return list;
        try {
            JsonNode root = objectMapper.readTree(transcriptJson);
            JsonNode meta = root.path("meta");
            if (meta.isMissingNode()) return list;
            JsonNode subs = meta.has("codeSubmissions") ? meta.get("codeSubmissions")
                : meta.has("codeSubmission") ? meta.get("codeSubmission") : null;
            if (subs == null) return list;
            if (!subs.isArray()) subs = objectMapper.createArrayNode().add(subs);
            for (JsonNode sub : subs) {
                if (sub == null || !sub.has("code")) continue;
                JsonNode aiReview = sub.path("aiReview");

                List<String> bugs = new ArrayList<>();
                if (aiReview.path("bugs").isArray()) aiReview.path("bugs").forEach(n -> {
                    String s = n.asText("").trim();
                    if (!s.isBlank()) bugs.add(s);
                });
                List<String> improvements = new ArrayList<>();
                if (aiReview.path("improvements").isArray()) aiReview.path("improvements").forEach(n -> {
                    String s = n.asText("").trim();
                    if (!s.isBlank()) improvements.add(s);
                });

                List<TestResult> testResults = new ArrayList<>();
                JsonNode results = sub.path("results");
                if (results.isArray()) {
                    for (JsonNode r : results) {
                        testResults.add(new TestResult(r.path("name").asText("Test"), r.path("passed").asBoolean(false)));
                    }
                }

                list.add(new CodeSub(
                    sub.path("language").asText(""),
                    sub.path("question").asText(""),
                    sub.path("code").asText(""),
                    aiReview.path("correctness").asText(""),
                    aiReview.path("score").asInt(0),
                    aiReview.path("overallFeedback").asText(""),
                    aiReview.path("timeComplexity").asText(""),
                    aiReview.path("spaceComplexity").asText(""),
                    sub.path("complexity").asText(""),
                    bugs,
                    improvements,
                    testResults
                ));
            }
        } catch (Exception e) {
            log.debug("Could not parse code submissions: {}", e.getMessage());
        }
        return list;
    }

    private InterviewQuality parseInterviewQuality(JsonNode assessment) {
        if (assessment == null || !assessment.has("interviewQuality")) return null;
        JsonNode iq = assessment.get("interviewQuality");
        List<String> covered = stringList(iq, "categoriesCovered");
        if (covered.isEmpty()) covered = stringList(iq, "covered");
        List<String> missed = stringList(iq, "categoriesMissed");
        if (missed.isEmpty()) missed = stringList(iq, "missed");
        return new InterviewQuality(
            iq.path("coverageScore").asInt(0),
            covered,
            missed,
            iq.path("note").asText("")
        );
    }

    private IntegritySummary parseIntegrity(Interview interview) {
        Integer score = interview.getProctoringScore();
        int tabSwitchCount = 0;
        boolean tabSwitchViolation = false;
        int fullscreenExitCount = 0;
        String status = null;
        try {
            String transcriptJson = interview.getTranscriptJson();
            if (transcriptJson != null && !transcriptJson.isBlank()) {
                JsonNode root = objectMapper.readTree(transcriptJson);
                JsonNode meta = root.path("meta");
                tabSwitchCount = meta.path("tabSwitchCount").asInt(0);
                tabSwitchViolation = meta.path("tabSwitchViolation").asBoolean(false);
                fullscreenExitCount = meta.path("fullscreenExitCount").asInt(0);
                JsonNode videoProctoring = meta.path("videoProctoring");
                if (!videoProctoring.isMissingNode()) {
                    status = videoProctoring.path("status").asText(null);
                    if (score == null && videoProctoring.has("integrityScore")) {
                        score = videoProctoring.path("integrityScore").asInt();
                    }
                }
            }
        } catch (Exception ignored) {}
        return new IntegritySummary(score, tabSwitchCount, tabSwitchViolation, fullscreenExitCount, status);
    }

    private JsonNode metaNode(String transcriptJson, String field) {
        if (transcriptJson == null || transcriptJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(transcriptJson);
            JsonNode meta = root.path("meta");
            if (!meta.isMissingNode() && meta.has(field)) return meta.get(field);
        } catch (Exception ignored) {}
        return null;
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
