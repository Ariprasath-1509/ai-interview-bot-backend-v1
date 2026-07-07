package com.benchreadiness.screening.entity;

import com.benchreadiness.screening.entity.enums.QuestionType;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "screening_questions", schema = "screening_svc")
public class ScreeningQuestion {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private ScreeningBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;

    @Column(columnDefinition = "text", nullable = false)
    private String prompt;

    /** JSON array of option strings; only populated for MCQ. */
    @Column(name = "options_json", columnDefinition = "text")
    private String optionsJson;

    /** For MCQ: the correct option text. For SNIPPET/LOGICAL/PROFICIENCY: a model answer/rubric used to guide AI grading. */
    @Column(name = "reference_answer", columnDefinition = "text")
    private String referenceAnswer;

    @Column(nullable = false)
    private int marks;

    @Column(name = "display_index", nullable = false)
    private int displayIndex;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public ScreeningBatch getBatch() { return batch; }
    public void setBatch(ScreeningBatch batch) { this.batch = batch; }
    public QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public void setReferenceAnswer(String referenceAnswer) { this.referenceAnswer = referenceAnswer; }
    public int getMarks() { return marks; }
    public void setMarks(int marks) { this.marks = marks; }
    public int getDisplayIndex() { return displayIndex; }
    public void setDisplayIndex(int displayIndex) { this.displayIndex = displayIndex; }
}
