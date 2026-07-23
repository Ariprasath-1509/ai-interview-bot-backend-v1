package com.benchreadiness.interview.service;

import com.benchreadiness.interview.dto.ClientBriefDto;
import com.benchreadiness.interview.dto.ClientBriefDto.QuestionAsked;
import com.benchreadiness.interview.dto.ClientBriefDto.SkillAssessment;
import com.benchreadiness.interview.dto.ClientBriefDto.SkillSummary;
import com.benchreadiness.interview.service.ClientBriefService.ClientBriefContext;
import com.benchreadiness.interview.service.ClientBriefService.ClientBriefPdfContext;
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
import java.util.List;

@Service
public class ClientBriefPdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ClientBriefPdfGenerationService.class);

    private static final Color BRAND_DARK = new Color(45, 27, 78);
    private static final Color BRAND_PURPLE = new Color(76, 45, 130);
    private static final Color BRAND_LIGHT = new Color(118, 75, 162);
    private static final Color ACCENT_GOLD = new Color(212, 168, 75);
    private static final Color PAGE_BG = new Color(252, 251, 255);
    private static final Color LIGHT_VIOLET_BG = new Color(248, 246, 255);
    private static final Color CARD_BORDER = new Color(210, 198, 235);
    private static final Color PROGRESS_BG = new Color(237, 233, 254);
    private static final Color SUCCESS = new Color(22, 120, 72);
    private static final Color SUCCESS_BG = new Color(236, 253, 245);
    private static final Color WARN = new Color(180, 83, 9);
    private static final Color WARN_BG = new Color(255, 251, 235);
    private static final Color DANGER = new Color(185, 28, 28);
    private static final Color DANGER_BG = new Color(254, 242, 242);
    private static final Color MUTED = new Color(90, 90, 102);
    private static final Color META_CHIP_BG = new Color(95, 68, 145);

    private static final Font BRAND_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, ACCENT_GOLD);
    private static final Font DOC_SUBTITLE = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(220, 210, 240));
    private static final Font HEADER_TITLE = new Font(Font.HELVETICA, 22, Font.BOLD, Color.WHITE);
    private static final Font HEADER_META_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_DARK);
    private static final Font SUBSECTION_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, BRAND_DARK);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(30, 30, 35));
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
    private static final Font SCORE_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, BRAND_LIGHT);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);

    public byte[] generateClientBriefPdf(ClientBriefPdfContext pdfContext) throws Exception {
        ClientBriefDto brief = pdfContext.brief();
        ClientBriefContext ctx = pdfContext.context();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 40, 64);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        String candidateName = safe(
            brief.getHeader() != null ? brief.getHeader().getCandidateName() : ctx.candidateName());
        writer.setPageEvent(new BriefPageEvent(candidateName));
        document.open();

        try {
            addHeaderBanner(document, brief, ctx);
            if (brief.getGenerationWarning() != null && !brief.getGenerationWarning().isBlank()) {
                addWarningBanner(document, brief.getGenerationWarning());
            }
            addScoreSummaryStrip(document, brief.getMustHaveSkills(), brief.getGoodToHaveSkills());
            addSkillSummaryGrid(document, brief.getMustHaveSkills(), brief.getGoodToHaveSkills());
            addQuestionsSection(document, brief.getQuestionsAsked());
            addOverallFeedback(document, brief.getOverallFeedback());
            addDetailedAssessments(document, brief.getSkillAssessments());
            addClosingMeta(document, brief);
        } catch (DocumentException e) {
            log.error("Client brief PDF generation failed: {}", e.getMessage(), e);
            throw e;
        }

        document.close();
        return baos.toByteArray();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeaderBanner(Document document, ClientBriefDto brief, ClientBriefContext ctx)
            throws DocumentException {
        ClientBriefDto.BriefHeader header = brief.getHeader();

        PdfPTable outer = new PdfPTable(1);
        outer.setWidthPercentage(100);
        outer.setSpacingAfter(16);
        outer.setKeepTogether(true);

        PdfPCell topBand = new PdfPCell();
        topBand.setBackgroundColor(BRAND_DARK);
        topBand.setBorder(Rectangle.NO_BORDER);
        topBand.setPadding(18);
        topBand.addElement(new Paragraph("BENCH READINESS", BRAND_FONT));
        topBand.addElement(spaced(new Paragraph("Client Evaluation Brief", DOC_SUBTITLE), 4, 8));
        topBand.addElement(new Paragraph(
            safe(header != null ? header.getCandidateName() : ctx.candidateName()), HEADER_TITLE));
        outer.addCell(topBand);

        PdfPTable metaStack = new PdfPTable(1);
        metaStack.setWidthPercentage(100);
        metaStack.addCell(nestedCell(buildHeaderMetaGrid(header, ctx)));
        ClientBriefDto.InterviewerInfo reviewer = header != null ? header.getReviewer() : null;
        String reviewerName = reviewer != null ? reviewer.getName() : ctx.reviewerName();
        if (reviewerName != null && !reviewerName.isBlank()) {
            metaStack.addCell(nestedCell(buildReviewerLine(reviewer, ctx)));
        }

        PdfPCell bottomBand = new PdfPCell(metaStack);
        bottomBand.setBackgroundColor(BRAND_PURPLE);
        bottomBand.setBorder(Rectangle.NO_BORDER);
        bottomBand.setPadding(18);
        outer.addCell(bottomBand);

        document.add(outer);
    }

    private PdfPTable buildHeaderMetaGrid(ClientBriefDto.BriefHeader header, ClientBriefContext ctx)
            throws DocumentException {
        PdfPTable grid = new PdfPTable(3);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[] {1f, 1f, 1f});
        addMetaChip(grid, "Position", header != null ? header.getPositionTitle() : ctx.jdTitle());
        addMetaChip(grid, "Client", firstNonBlank(header != null ? header.getClientName() : null, ctx.clientName()));
        addMetaChip(grid, "Round", header != null ? header.getRoundName() : ctx.roundName());
        addMetaChip(grid, "Seniority", header != null ? header.getSeniorityBand() : ctx.seniorityBand());
        addMetaChip(grid, "Interview date",
            formatInterviewDate(header != null ? header.getInterviewDate() : ctx.interviewDate()));
        Integer minutes = header != null ? header.getTotalMinutes() : ctx.totalMinutes();
        addMetaChip(grid, "Duration", minutes != null && minutes > 0 ? minutes + " min" : null);
        return grid;
    }

    private void addMetaChip(PdfPTable grid, String label, String value) {
        if (value == null || value.isBlank()) {
            PdfPCell empty = new PdfPCell(new Phrase(" "));
            empty.setBorder(Rectangle.NO_BORDER);
            empty.setPadding(4);
            grid.addCell(empty);
            return;
        }
        PdfPCell chip = new PdfPCell();
        chip.setBackgroundColor(META_CHIP_BG);
        chip.setBorder(Rectangle.NO_BORDER);
        chip.setPadding(8);
        Paragraph labelLine = new Paragraph(label.toUpperCase(), new Font(Font.HELVETICA, 7, Font.BOLD, ACCENT_GOLD));
        labelLine.setSpacingAfter(3);
        chip.addElement(labelLine);
        chip.addElement(new Paragraph(value, HEADER_META_BOLD));
        grid.addCell(chip);
    }

    private PdfPTable buildReviewerLine(ClientBriefDto.InterviewerInfo reviewer, ClientBriefContext ctx) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(META_CHIP_BG);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(10);
        cell.addElement(new Paragraph("Reviewed by", new Font(Font.HELVETICA, 7, Font.BOLD, ACCENT_GOLD)));
        String name = reviewer != null && reviewer.getName() != null ? reviewer.getName() : ctx.reviewerName();
        String company = reviewer != null ? reviewer.getCompany() : ctx.reviewerCompany();
        String yoe = reviewer != null ? reviewer.getYearsExperience() : ctx.reviewerYoe();
        StringBuilder line = new StringBuilder(safe(name));
        if (company != null && !company.isBlank()) line.append("  |  ").append(company);
        if (yoe != null && !yoe.isBlank()) line.append("  |  ").append(yoe).append(" yrs");
        cell.addElement(new Paragraph(line.toString(), HEADER_META_BOLD));
        box.addCell(cell);
        return box;
    }

    // ── Summary stats ─────────────────────────────────────────────────────────

    private void addScoreSummaryStrip(Document document, List<SkillSummary> mustHave, List<SkillSummary> goodToHave)
            throws DocumentException {
        PdfPTable strip = new PdfPTable(3);
        strip.setWidthPercentage(100);
        strip.setSpacingAfter(16);
        strip.setWidths(new float[] {1f, 1f, 1f});
        strip.setKeepTogether(true);
        strip.addCell(statCard("Must-have avg", averageScore(mustHave), countSkills(mustHave), true));
        strip.addCell(statCard("Good-to-have avg", averageScore(goodToHave), countSkills(goodToHave), true));
        strip.addCell(statCard("Skills assessed",
            averageScore(combineSkills(mustHave, goodToHave)),
            countSkills(mustHave) + countSkills(goodToHave), false));
        document.add(strip);
    }

    private PdfPCell statCard(String label, double avg, int count, boolean scoreMain) {
        PdfPCell cell = borderedCell(Color.WHITE);
        cell.setPadding(14);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph labelP = new Paragraph(label.toUpperCase(), new Font(Font.HELVETICA, 7, Font.BOLD, MUTED));
        labelP.setAlignment(Element.ALIGN_CENTER);
        labelP.setSpacingAfter(8);
        cell.addElement(labelP);

        if (scoreMain) {
            int rounded = (int) Math.round(avg);
            Paragraph score = new Paragraph(
                count > 0 ? rounded + " / 10" : "-",
                new Font(Font.HELVETICA, 22, Font.BOLD, scoreColor(Math.max(0, rounded))));
            score.setAlignment(Element.ALIGN_CENTER);
            score.setSpacingAfter(4);
            cell.addElement(score);
            Paragraph sub = new Paragraph(count + " skill" + (count == 1 ? "" : "s"), SMALL_FONT);
            sub.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(sub);
        } else {
            Paragraph countP = new Paragraph(String.valueOf(count), new Font(Font.HELVETICA, 22, Font.BOLD, BRAND_DARK));
            countP.setAlignment(Element.ALIGN_CENTER);
            countP.setSpacingAfter(4);
            cell.addElement(countP);
            int rounded = (int) Math.round(avg);
            Paragraph sub = new Paragraph(count > 0 ? "avg " + rounded + " / 10" : "No skills", SMALL_FONT);
            sub.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(sub);
        }
        return cell;
    }

    // ── Skill summary ─────────────────────────────────────────────────────────

    private void addSkillSummaryGrid(Document document, List<SkillSummary> mustHave, List<SkillSummary> goodToHave)
            throws DocumentException {
        document.add(sectionTitle("Skill summary"));
        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setSpacingAfter(12);
        grid.setWidths(new float[] {1f, 1f});
        grid.addCell(skillColumn("Must have", mustHave, BRAND_PURPLE));
        grid.addCell(skillColumn("Good to have", goodToHave, BRAND_LIGHT));
        document.add(grid);
    }

    private PdfPCell skillColumn(String title, List<SkillSummary> skills, Color accent) throws DocumentException {
        PdfPTable column = new PdfPTable(1);
        column.setWidthPercentage(100);

        PdfPCell titleCell = textCell(new Paragraph(title, new Font(Font.HELVETICA, 12, Font.BOLD, accent)));
        titleCell.setPaddingBottom(6);
        column.addCell(titleCell);
        column.addCell(nestedCell(accentBar(accent)));

        if (skills == null || skills.isEmpty()) {
            column.addCell(gapCell(8));
            column.addCell(textCell(new Paragraph("No skills in this group.", SMALL_FONT)));
        } else {
            for (SkillSummary skill : skills) {
                column.addCell(gapCell(10));
                column.addCell(nestedCell(skillBlock(skill)));
            }
        }

        PdfPCell wrapper = new PdfPCell(column);
        wrapper.setBackgroundColor(Color.WHITE);
        wrapper.setBorderColor(CARD_BORDER);
        wrapper.setBorderWidth(0.75f);
        wrapper.setPadding(14);
        return wrapper;
    }

    /** One skill: title row + progress bar row — avoids overlap. */
    private PdfPTable skillBlock(SkillSummary skill) throws DocumentException {
        PdfPTable block = new PdfPTable(new float[] {1f, 0.18f});
        block.setWidthPercentage(100);

        Paragraph label = new Paragraph();
        label.add(new Chunk(skill.getRank() + ". ", SCORE_FONT));
        label.add(new Chunk(formatSkillLabel(skill.getSkill(), skill.getSubSkill()), SUBSECTION_FONT));

        PdfPCell labelCell = textCell(label);
        labelCell.setVerticalAlignment(Element.ALIGN_TOP);
        block.addCell(labelCell);
        block.addCell(scoreBadge(skill.getScore10()));

        if (skill.getNote() != null && !skill.getNote().isBlank()) {
            PdfPCell noteCell = textCell(new Paragraph(skill.getNote(), SMALL_FONT));
            noteCell.setColspan(2);
            noteCell.setPaddingTop(2);
            noteCell.setPaddingBottom(4);
            block.addCell(noteCell);
        }

        PdfPCell barCell = nestedCell(progressBar(skill.getScore10()));
        barCell.setColspan(2);
        barCell.setPaddingTop(6);
        block.addCell(barCell);

        return block;
    }

    // ── Questions ─────────────────────────────────────────────────────────────

    private void addQuestionsSection(Document document, List<QuestionAsked> questions) throws DocumentException {
        document.add(sectionTitle("Questions asked"));
        if (questions == null || questions.isEmpty()) {
            document.add(simpleBox(new Paragraph("No questions recorded.", SMALL_FONT)));
            return;
        }
        for (int i = 0; i < questions.size(); i++) {
            PdfPTable card = questionCard(questions.get(i));
            card.setSpacingAfter(i < questions.size() - 1 ? 10 : 12);
            card.setKeepTogether(true);
            document.add(card);
        }
    }

    private PdfPTable questionCard(QuestionAsked q) throws DocumentException {
        PdfPTable card = new PdfPTable(new float[] {0.015f, 0.985f});
        card.setWidthPercentage(100);

        PdfPCell stripe = new PdfPCell();
        stripe.setBackgroundColor(BRAND_LIGHT);
        stripe.setBorder(Rectangle.NO_BORDER);
        card.addCell(stripe);

        PdfPTable body = new PdfPTable(1);
        body.setWidthPercentage(100);

        PdfPTable head = new PdfPTable(new float[] {1f, 0.28f});
        head.setWidthPercentage(100);
        head.addCell(textCell(new Paragraph("Q" + q.getNumber(),
            new Font(Font.HELVETICA, 10, Font.BOLD, BRAND_LIGHT))));
        head.addCell(difficultyBadge(safe(q.getDifficulty())));
        body.addCell(nestedCell(head));

        PdfPCell textCell = textCell(new Paragraph(safe(q.getText()), BODY_FONT));
        textCell.setPaddingTop(8);
        textCell.setPaddingBottom(q.getAnswer() != null && !q.getAnswer().isBlank() ? 2 : 6);
        body.addCell(textCell);

        if (q.getAnswer() != null && !q.getAnswer().isBlank()) {
            PdfPCell answerLabel = textCell(new Paragraph("Candidate's answer", SMALL_FONT));
            answerLabel.setPaddingTop(6);
            answerLabel.setPaddingBottom(2);
            body.addCell(answerLabel);

            PdfPCell answerCell = textCell(new Paragraph(safe(q.getAnswer()), BODY_FONT));
            answerCell.setPaddingBottom(6);
            body.addCell(answerCell);
        }

        if (q.getSkillMappings() != null && !q.getSkillMappings().isEmpty()) {
            body.addCell(nestedCell(skillTags(q)));
        }

        PdfPCell bodyWrap = new PdfPCell(body);
        bodyWrap.setBackgroundColor(LIGHT_VIOLET_BG);
        bodyWrap.setBorderColor(CARD_BORDER);
        bodyWrap.setBorderWidth(0.75f);
        bodyWrap.setPadding(12);
        card.addCell(bodyWrap);
        return card;
    }

    private PdfPTable skillTags(QuestionAsked q) {
        StringBuilder sb = new StringBuilder();
        q.getSkillMappings().forEach(m -> {
            if (!sb.isEmpty()) sb.append("   |   ");
            sb.append(m.getSkill()).append(": ").append(m.getSubSkill());
        });
        PdfPTable bar = new PdfPTable(1);
        bar.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(sb.toString(), new Font(Font.HELVETICA, 7, Font.NORMAL, BRAND_PURPLE)));
        cell.setBackgroundColor(new Color(237, 233, 254));
        cell.setBorderColor(CARD_BORDER);
        cell.setPadding(7);
        bar.addCell(cell);
        return bar;
    }

    // ── Overall feedback ──────────────────────────────────────────────────────

    private void addOverallFeedback(Document document, String feedback) throws DocumentException {
        document.add(sectionTitle("Overall feedback"));
        PdfPTable card = new PdfPTable(new float[] {0.015f, 0.985f});
        card.setWidthPercentage(100);
        card.setSpacingAfter(12);
        card.setKeepTogether(true);

        PdfPCell stripe = new PdfPCell();
        stripe.setBackgroundColor(BRAND_LIGHT);
        stripe.setBorder(Rectangle.NO_BORDER);
        card.addCell(stripe);

        String text = feedback != null && !feedback.isBlank() ? feedback : "No overall feedback provided.";
        PdfPCell content = textCell(new Paragraph(text, BODY_FONT));
        content.setBackgroundColor(Color.WHITE);
        content.setBorderColor(CARD_BORDER);
        content.setBorderWidth(0.75f);
        content.setPadding(14);
        card.addCell(content);
        document.add(card);
    }

    // ── Detailed assessments ──────────────────────────────────────────────────

    private void addDetailedAssessments(Document document, List<SkillAssessment> assessments)
            throws DocumentException {
        if (assessments == null || assessments.isEmpty()) return;
        document.add(sectionTitle("Detailed skill assessment"));
        for (int i = 0; i < assessments.size(); i++) {
            PdfPTable card = assessmentCard(assessments.get(i));
            card.setSpacingAfter(i < assessments.size() - 1 ? 12 : 0);
            document.add(card);
        }
    }

    private PdfPTable assessmentCard(SkillAssessment a) throws DocumentException {
        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100);

        PdfPTable inner = new PdfPTable(1);
        inner.setWidthPercentage(100);

        PdfPTable titleRow = new PdfPTable(new float[] {1f, 0.18f});
        titleRow.setWidthPercentage(100);
        Paragraph heading = new Paragraph();
        heading.add(new Chunk(a.getRank() + ". ", SCORE_FONT));
        heading.add(new Chunk(formatSkillLabel(a.getSkill(), a.getSubSkill()), SUBSECTION_FONT));
        titleRow.addCell(textCell(heading));
        titleRow.addCell(scoreBadge(a.getScore10()));
        inner.addCell(nestedCell(titleRow));

        inner.addCell(gapCell(8));
        inner.addCell(nestedCell(accentBar(BRAND_LIGHT)));
        inner.addCell(gapCell(8));
        inner.addCell(nestedCell(proficiencyBox(a)));
        inner.addCell(gapCell(10));
        inner.addCell(nestedCell(strengthGrid(a)));

        PdfPCell wrap = new PdfPCell(inner);
        wrap.setBackgroundColor(Color.WHITE);
        wrap.setBorderColor(CARD_BORDER);
        wrap.setBorderWidth(0.75f);
        wrap.setPadding(14);
        card.addCell(wrap);
        return card;
    }

    private PdfPTable proficiencyBox(SkillAssessment a) {
        List<String> options = a.getProficiencyOptions();
        int idx = a.getSelectedProficiencyIndex();
        String text = "Proficiency not specified.";
        if (options != null && !options.isEmpty()) {
            text = options.get(Math.max(0, Math.min(idx, options.size() - 1)));
        }
        int score = a.getScore10();
        Color bg = score >= 7 ? SUCCESS_BG : (score >= 5 ? WARN_BG : DANGER_BG);
        Color fg = score >= 7 ? SUCCESS : (score >= 5 ? WARN : DANGER);

        PdfPCell cell = textCell(new Paragraph(text, new Font(Font.HELVETICA, 10, Font.BOLD, fg)));
        cell.setBackgroundColor(bg);
        cell.setPadding(10);
        cell.setBorder(Rectangle.NO_BORDER);

        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.addCell(cell);
        return box;
    }

    private PdfPTable strengthGrid(SkillAssessment a) throws DocumentException {
        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[] {1f, 1f});
        grid.addCell(bulletBox("Strengths", a.getStrengths(), SUCCESS_BG, SUCCESS));
        grid.addCell(bulletBox("Areas of improvement", a.getAreasOfImprovement(), WARN_BG, WARN));
        return grid;
    }

    private PdfPCell bulletBox(String label, List<String> items, Color bg, Color labelColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(CARD_BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPadding(10);
        Paragraph title = new Paragraph(label, new Font(Font.HELVETICA, 9, Font.BOLD, labelColor));
        title.setSpacingAfter(6);
        cell.addElement(title);
        addBulletParagraphs(cell, items);
        return cell;
    }

    private void addBulletParagraphs(PdfPCell cell, List<String> items) {
        if (items == null || items.isEmpty()) {
            Paragraph p = new Paragraph("- Not specified.", BODY_FONT);
            p.setLeading(14);
            cell.addElement(p);
            return;
        }
        for (String item : items) {
            Paragraph p = new Paragraph("- " + item, BODY_FONT);
            p.setLeading(14);
            p.setSpacingAfter(3);
            cell.addElement(p);
        }
    }

    // ── Misc sections ─────────────────────────────────────────────────────────

    private void addWarningBanner(Document document, String warning) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[] {0.015f, 0.985f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(14);
        table.setKeepTogether(true);
        PdfPCell stripe = new PdfPCell();
        stripe.setBackgroundColor(new Color(217, 119, 6));
        stripe.setBorder(Rectangle.NO_BORDER);
        table.addCell(stripe);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(WARN_BG);
        cell.setBorderColor(new Color(245, 158, 11));
        cell.setPadding(12);
        cell.addElement(new Paragraph("Note", new Font(Font.HELVETICA, 9, Font.BOLD, WARN)));
        Paragraph p = new Paragraph(warning, new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(120, 53, 15)));
        p.setLeading(14);
        cell.addElement(p);
        table.addCell(cell);
        document.add(table);
    }

    private void addClosingMeta(Document document, ClientBriefDto brief) throws DocumentException {
        String edited = brief.getLastEditedByName() != null && !brief.getLastEditedByName().isBlank()
            ? "Reviewed by " + brief.getLastEditedByName() + "  |  " : "";
        Paragraph meta = new Paragraph(edited + "Generated " + formatDisplayDate(Instant.now().toString()), FOOTER_FONT);
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingBefore(16);
        document.add(meta);
    }

    private PdfPTable simpleBox(Paragraph content) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = borderedCell(Color.WHITE);
        cell.setPadding(14);
        cell.addElement(content);
        table.addCell(cell);
        return table;
    }

    // ── Layout helpers (OpenPDF-safe: nest tables only via PdfPCell(table) + addCell) ──

    private Paragraph sectionTitle(String title) {
        Paragraph p = new Paragraph(title, SECTION_FONT);
        p.setSpacingBefore(10);
        p.setSpacingAfter(10);
        return p;
    }

    private PdfPCell nestedCell(PdfPTable table) {
        PdfPCell cell = new PdfPCell(table);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfPCell textCell(Paragraph paragraph) {
        PdfPCell cell = new PdfPCell(paragraph);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfPCell gapCell(float height) {
        PdfPCell cell = new PdfPCell(new Phrase(" "));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(height);
        cell.setPadding(0);
        return cell;
    }

    private PdfPCell borderedCell(Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(CARD_BORDER);
        cell.setBorderWidth(0.75f);
        return cell;
    }

    private PdfPTable accentBar(Color color) {
        PdfPTable bar = new PdfPTable(1);
        bar.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(color);
        cell.setFixedHeight(2.5f);
        cell.setBorder(Rectangle.NO_BORDER);
        bar.addCell(cell);
        return bar;
    }

    private PdfPTable progressBar(int score) throws DocumentException {
        int filled = Math.max(1, Math.min(10, score));
        int empty = Math.max(1, 10 - filled);
        PdfPTable bar = new PdfPTable(new float[] {filled, empty});
        bar.setWidthPercentage(100);
        PdfPCell filledCell = new PdfPCell();
        filledCell.setBackgroundColor(scoreColor(score));
        filledCell.setFixedHeight(7);
        filledCell.setBorder(Rectangle.NO_BORDER);
        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBackgroundColor(PROGRESS_BG);
        emptyCell.setFixedHeight(7);
        emptyCell.setBorder(Rectangle.NO_BORDER);
        bar.addCell(filledCell);
        bar.addCell(emptyCell);
        return bar;
    }

    private PdfPCell scoreBadge(int score) {
        int clamped = Math.max(0, Math.min(10, score));
        PdfPCell cell = new PdfPCell(new Phrase(
            clamped + "/10", new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(scoreColor(clamped));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(5);
        cell.setPaddingBottom(5);
        cell.setPaddingLeft(4);
        cell.setPaddingRight(4);
        cell.setNoWrap(true);
        return cell;
    }

    private PdfPCell difficultyBadge(String difficulty) {
        Color bg = switch (difficulty.toLowerCase()) {
            case "easy" -> new Color(34, 139, 84);
            case "hard" -> new Color(185, 28, 28);
            default -> new Color(217, 119, 6);
        };
        PdfPCell cell = new PdfPCell(new Phrase(
            difficulty.toUpperCase(), new Font(Font.HELVETICA, 7, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setNoWrap(true);
        return cell;
    }

    private Paragraph spaced(Paragraph p, float before, float after) {
        p.setSpacingBefore(before);
        p.setSpacingAfter(after);
        return p;
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    private String formatSkillLabel(String skill, String subSkill) {
        if (skill == null || skill.isBlank()) return safe(subSkill);
        if (subSkill == null || subSkill.isBlank()) return skill;
        if (skill.equalsIgnoreCase(subSkill.trim())) return skill;
        return skill + " - " + subSkill;
    }

    private Color scoreColor(int score) {
        if (score >= 7) return SUCCESS;
        if (score >= 5) return new Color(217, 119, 6);
        return DANGER;
    }

    private double averageScore(List<SkillSummary> skills) {
        if (skills == null || skills.isEmpty()) return 0;
        return skills.stream().mapToInt(SkillSummary::getScore10).average().orElse(0);
    }

    private int countSkills(List<SkillSummary> skills) {
        return skills == null ? 0 : skills.size();
    }

    private List<SkillSummary> combineSkills(List<SkillSummary> a, List<SkillSummary> b) {
        java.util.ArrayList<SkillSummary> combined = new java.util.ArrayList<>();
        if (a != null) combined.addAll(a);
        if (b != null) combined.addAll(b);
        return combined;
    }

    private String safe(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary;
        return fallback;
    }

    private String formatInterviewDate(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
                .withZone(ZoneId.systemDefault()).format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    private String formatDisplayDate(String iso) {
        try {
            return DateTimeFormatter.ofPattern("d MMM yyyy")
                .withZone(ZoneId.systemDefault()).format(Instant.parse(iso));
        } catch (Exception e) {
            return DateTimeFormatter.ofPattern("d MMM yyyy")
                .withZone(ZoneId.systemDefault()).format(Instant.now());
        }
    }

    // ── Page header/footer ────────────────────────────────────────────────────

    private static final class BriefPageEvent extends PdfPageEventHelper {
        private final String candidateName;
        private PdfTemplate totalPages;
        private BaseFont baseFont;

        BriefPageEvent(String candidateName) {
            this.candidateName = candidateName;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPages = writer.getDirectContent().createTemplate(24, 14);
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                baseFont = null;
            }
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
                canvas.setColorFill(BRAND_LIGHT);
                canvas.rectangle(left, top, right - left, 2f);
                canvas.fill();
                canvas.beginText();
                canvas.setFontAndSize(baseFont, 8);
                canvas.setColorFill(BRAND_DARK);
                canvas.showTextAligned(Element.ALIGN_LEFT, "Bench Readiness  |  Client Evaluation Brief", left, top - 12, 0);
                canvas.setColorFill(MUTED);
                canvas.showTextAligned(Element.ALIGN_RIGHT, candidateName, right, top - 12, 0);
                canvas.endText();
            }

            float lineY = document.bottomMargin() - 10;
            canvas.setColorFill(CARD_BORDER);
            canvas.rectangle(left, lineY + 8, right - left, 0.5f);
            canvas.fill();

            canvas.beginText();
            canvas.setFontAndSize(baseFont, 7);
            canvas.setColorFill(MUTED);
            canvas.showTextAligned(Element.ALIGN_LEFT, "Confidential", left, lineY, 0);
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
