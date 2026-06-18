package com.benchreadiness.interview.dto;

import com.benchreadiness.interview.entity.InterviewQuestion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class InterviewQuestionDto {
    private Long id;
    private String interviewId;
    private int slotNumber;
    private String questionText;
    private String candidateAnswer;
    private List<String> tags;
    private String difficultyLevel;
    private String questionType;
    private LocalDateTime askedAt;
    private LocalDateTime answeredAt;
    private String questionBankId;
    private String source;

    public static InterviewQuestionDto from(InterviewQuestion question) {
        InterviewQuestionDto dto = new InterviewQuestionDto();
        dto.id = question.getId();
        dto.interviewId = question.getInterviewId();
        dto.slotNumber = question.getSlotNumber();
        dto.questionText = question.getQuestionText();
        dto.candidateAnswer = question.getCandidateAnswer();
        dto.tags = question.getTags() != null ? new ArrayList<>(question.getTags()) : List.of();
        dto.difficultyLevel = question.getDifficultyLevel();
        dto.questionType = question.getQuestionType();
        dto.askedAt = question.getAskedAt();
        dto.answeredAt = question.getAnsweredAt();
        dto.questionBankId = question.getQuestionBankId();
        dto.source = question.getSource();
        return dto;
    }

    public Long getId() { return id; }
    public String getInterviewId() { return interviewId; }
    public int getSlotNumber() { return slotNumber; }
    public String getQuestionText() { return questionText; }
    public String getCandidateAnswer() { return candidateAnswer; }
    public List<String> getTags() { return tags; }
    public String getDifficultyLevel() { return difficultyLevel; }
    public String getQuestionType() { return questionType; }
    public LocalDateTime getAskedAt() { return askedAt; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public String getQuestionBankId() { return questionBankId; }
    public String getSource() { return source; }
}
