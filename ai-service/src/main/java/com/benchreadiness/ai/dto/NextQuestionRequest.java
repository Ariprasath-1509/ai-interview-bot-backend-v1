package com.benchreadiness.ai.dto;

import java.util.List;

public class NextQuestionRequest {

    private int slot;
    private String lastAnswer;
    private String jdTitle;
    private String jdText;
    private String resumeSummary;
    private String focusAreas;
    private String candidateHint;
    private List<Utterance> utterances;
    private int manipulationCount;
    private String rubricJson;
    private String candidateProfileJson;
    private String interviewMode;
    /** EASY | MEDIUM | HARD — explicit per-round override; null/blank derives difficulty from interviewMode. */
    private String questionDifficulty;
    private String interviewId;
    private String questionBankQuestionsJson;
    private String customQuestionsJson;
    private String usedQuestionIds;
    /** When false, theory/verbal only — no coding slot or code editor. Null defaults to true. */
    private Boolean includeProgrammingQuestions;
    /** CLIENT_INTERVIEW (default) | ONBOARDING — onboarding uses concept-scoped themes instead of JD-role themes. */
    private String assessmentType;
    /** Ordered (slot 1..N), comma-separated question "source" values already persisted for this
     *  interview (e.g. "AI_GENERATED,QUESTION_BANK,AI_CROSS_QUESTION,..."). Used to enforce a real
     *  once-per-5-slots cross-question cadence instead of a text-similarity proxy. Null/blank is
     *  treated as no history (cadence gate open). */
    private String recentQuestionSources;
    /** correct | partial | incorrect — AI-review verdict of the most recent code submission, if any.
     *  Drives whether a second (easy) coding slot is offered. Null when no code has been submitted yet. */
    private String lastCodeCorrectness;

    public record Utterance(String speaker, String text) {}

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
    public String getLastAnswer() { return lastAnswer; }
    public void setLastAnswer(String lastAnswer) { this.lastAnswer = lastAnswer; }
    public String getJdTitle() { return jdTitle; }
    public void setJdTitle(String jdTitle) { this.jdTitle = jdTitle; }
    public String getJdText() { return jdText; }
    public void setJdText(String jdText) { this.jdText = jdText; }
    public String getResumeSummary() { return resumeSummary; }
    public void setResumeSummary(String resumeSummary) { this.resumeSummary = resumeSummary; }
    public String getFocusAreas() { return focusAreas; }
    public void setFocusAreas(String focusAreas) { this.focusAreas = focusAreas; }
    public String getCandidateHint() { return candidateHint; }
    public void setCandidateHint(String candidateHint) { this.candidateHint = candidateHint; }
    public List<Utterance> getUtterances() { return utterances; }
    public void setUtterances(List<Utterance> utterances) { this.utterances = utterances; }
    public int getManipulationCount() { return manipulationCount; }
    public void setManipulationCount(int manipulationCount) { this.manipulationCount = manipulationCount; }
    public String getRubricJson() { return rubricJson; }
    public void setRubricJson(String rubricJson) { this.rubricJson = rubricJson; }
    public String getCandidateProfileJson() { return candidateProfileJson; }
    public void setCandidateProfileJson(String candidateProfileJson) { this.candidateProfileJson = candidateProfileJson; }
    public String getInterviewMode() { return interviewMode; }
    public void setInterviewMode(String interviewMode) { this.interviewMode = interviewMode; }
    public String getQuestionDifficulty() { return questionDifficulty; }
    public void setQuestionDifficulty(String questionDifficulty) { this.questionDifficulty = questionDifficulty; }
    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getQuestionBankQuestionsJson() { return questionBankQuestionsJson; }
    public void setQuestionBankQuestionsJson(String questionBankQuestionsJson) { this.questionBankQuestionsJson = questionBankQuestionsJson; }
    public String getCustomQuestionsJson() { return customQuestionsJson; }
    public void setCustomQuestionsJson(String customQuestionsJson) { this.customQuestionsJson = customQuestionsJson; }
    public String getUsedQuestionIds() { return usedQuestionIds; }
    public void setUsedQuestionIds(String usedQuestionIds) { this.usedQuestionIds = usedQuestionIds; }
    public Boolean getIncludeProgrammingQuestions() { return includeProgrammingQuestions; }
    public void setIncludeProgrammingQuestions(Boolean includeProgrammingQuestions) {
        this.includeProgrammingQuestions = includeProgrammingQuestions;
    }
    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }
    public boolean isOnboarding() { return "ONBOARDING".equals(assessmentType); }
    public String getRecentQuestionSources() { return recentQuestionSources; }
    public void setRecentQuestionSources(String recentQuestionSources) { this.recentQuestionSources = recentQuestionSources; }
    public String getLastCodeCorrectness() { return lastCodeCorrectness; }
    public void setLastCodeCorrectness(String lastCodeCorrectness) { this.lastCodeCorrectness = lastCodeCorrectness; }
}
