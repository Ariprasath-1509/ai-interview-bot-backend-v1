package com.benchreadiness.screening.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class SubmitAnswersRequest {

    @NotBlank
    private String candidateEmailConfirmation;

    @NotEmpty
    private List<AnswerEntry> answers;

    /** Fullscreen exits + tab switches the candidate's browser detected during the test, if any. */
    private Integer tabSwitchCount;

    public String getCandidateEmailConfirmation() { return candidateEmailConfirmation; }
    public void setCandidateEmailConfirmation(String candidateEmailConfirmation) { this.candidateEmailConfirmation = candidateEmailConfirmation; }
    public List<AnswerEntry> getAnswers() { return answers; }
    public void setAnswers(List<AnswerEntry> answers) { this.answers = answers; }
    public Integer getTabSwitchCount() { return tabSwitchCount; }
    public void setTabSwitchCount(Integer tabSwitchCount) { this.tabSwitchCount = tabSwitchCount; }

    public static class AnswerEntry {
        @NotBlank
        private String questionId;
        private String rawAnswer;

        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }
        public String getRawAnswer() { return rawAnswer; }
        public void setRawAnswer(String rawAnswer) { this.rawAnswer = rawAnswer; }
    }
}
