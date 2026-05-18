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
    private String interviewId;
    private String questionBankQuestionsJson;
    private String usedQuestionIds;

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
    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getQuestionBankQuestionsJson() { return questionBankQuestionsJson; }
    public void setQuestionBankQuestionsJson(String questionBankQuestionsJson) { this.questionBankQuestionsJson = questionBankQuestionsJson; }
    public String getUsedQuestionIds() { return usedQuestionIds; }
    public void setUsedQuestionIds(String usedQuestionIds) { this.usedQuestionIds = usedQuestionIds; }
}
