package com.benchreadiness.compliance.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "llm_configuration", schema = "compliance_svc")
public class LlmConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "claude_question_model", length = 100)
    private String claudeQuestionModel;

    @Column(name = "claude_assessment_model", length = 100)
    private String claudeAssessmentModel;

    @Column(name = "claude_rubric_model", length = 100)
    private String claudeRubricModel;

    @Column(name = "claude_matching_model", length = 100)
    private String claudeMatchingModel;

    @Column(name = "ollama_question_model", length = 100)
    private String ollamaQuestionModel;

    @Column(name = "ollama_assessment_model", length = 100)
    private String ollamaAssessmentModel;

    @Column(name = "ollama_rubric_model", length = 100)
    private String ollamaRubricModel;

    @Column(name = "ollama_matching_model", length = 100)
    private String ollamaMatchingModel;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getClaudeQuestionModel() { return claudeQuestionModel; }
    public void setClaudeQuestionModel(String claudeQuestionModel) { this.claudeQuestionModel = claudeQuestionModel; }

    public String getClaudeAssessmentModel() { return claudeAssessmentModel; }
    public void setClaudeAssessmentModel(String claudeAssessmentModel) { this.claudeAssessmentModel = claudeAssessmentModel; }

    public String getClaudeRubricModel() { return claudeRubricModel; }
    public void setClaudeRubricModel(String claudeRubricModel) { this.claudeRubricModel = claudeRubricModel; }

    public String getClaudeMatchingModel() { return claudeMatchingModel; }
    public void setClaudeMatchingModel(String claudeMatchingModel) { this.claudeMatchingModel = claudeMatchingModel; }

    public String getOllamaQuestionModel() { return ollamaQuestionModel; }
    public void setOllamaQuestionModel(String ollamaQuestionModel) { this.ollamaQuestionModel = ollamaQuestionModel; }

    public String getOllamaAssessmentModel() { return ollamaAssessmentModel; }
    public void setOllamaAssessmentModel(String ollamaAssessmentModel) { this.ollamaAssessmentModel = ollamaAssessmentModel; }

    public String getOllamaRubricModel() { return ollamaRubricModel; }
    public void setOllamaRubricModel(String ollamaRubricModel) { this.ollamaRubricModel = ollamaRubricModel; }

    public String getOllamaMatchingModel() { return ollamaMatchingModel; }
    public void setOllamaMatchingModel(String ollamaMatchingModel) { this.ollamaMatchingModel = ollamaMatchingModel; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
