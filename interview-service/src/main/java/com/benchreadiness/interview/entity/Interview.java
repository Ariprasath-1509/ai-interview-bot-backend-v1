package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "interviews", schema = "interview_svc")
public class Interview {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "engineer_id", nullable = false)
    private String engineerId;

    @Column(name = "jd_id", nullable = false)
    private String jdId;

    @Column(name = "plan_id", unique = true)
    private String planId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "interview_svc.interview_status")
    private InterviewStatus status = InterviewStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "interview_mode", columnDefinition = "interview_svc.interview_mode")
    private InterviewMode interviewMode = InterviewMode.SCREENING;

    @Column(name = "custom_duration_minutes")
    private Integer customDurationMinutes;

    @Column(name = "created_by_user_id")
    private String createdByUserId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "transcript_json", columnDefinition = "TEXT")
    private String transcriptJson;

    @Column(name = "audio_storage_key")
    private String audioStorageKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "proposed_verdict", columnDefinition = "interview_svc.readiness_verdict")
    private ReadinessVerdict proposedVerdict;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "final_verdict", columnDefinition = "interview_svc.readiness_verdict")
    private ReadinessVerdict finalVerdict;

    @Column(name = "sign_off_by_user_id")
    private String signOffByUserId;

    @Column(name = "sign_off_note", columnDefinition = "TEXT")
    private String signOffNote;

    @Column(name = "signed_off_at")
    private Instant signedOffAt;

    @Column(name = "question_bank_questions_json", columnDefinition = "TEXT")
    private String questionBankQuestionsJson;
    
    @Column(name = "custom_questions_json", columnDefinition = "TEXT")
    private String customQuestionsJson;

    @Column(name = "include_programming_questions", nullable = false)
    private boolean includeProgrammingQuestions = true;

    @Column(name = "used_question_ids", columnDefinition = "TEXT")
    private String usedQuestionIds = "";

    @Column(name = "recording_path", columnDefinition = "TEXT")
    private String recordingPath;

    @Column(name = "assessment_status", length = 32)
    private String assessmentStatus;

    @Column(name = "assessment_error", columnDefinition = "TEXT")
    private String assessmentError;

    @Column(name = "assessment_result_json", columnDefinition = "TEXT")
    private String assessmentResultJson;

    @Column(name = "recording_bytes")
    private Long recordingBytes;

    @Column(name = "proctoring_events_json", columnDefinition = "TEXT")
    private String proctoringEventsJson;

    @Column(name = "proctoring_score")
    private Integer proctoringScore;

    @Column(name = "proctoring_snapshots_json", columnDefinition = "TEXT")
    private String proctoringSnapshotsJson;

    /** Resolved from auth candidate profile — not persisted. */
    @Transient
    private String candidateSource;

    /** "video" or "light" based on admin proctoring settings for candidate source — not persisted. */
    @Transient
    private String proctoringMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public String getEngineerId() { return engineerId; }
    public void setEngineerId(String engineerId) { this.engineerId = engineerId; }
    public String getJdId() { return jdId; }
    public void setJdId(String jdId) { this.jdId = jdId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public InterviewStatus getStatus() { return status; }
    public void setStatus(InterviewStatus status) { this.status = status; }
    public InterviewMode getInterviewMode() { return interviewMode; }
    public void setInterviewMode(InterviewMode interviewMode) { this.interviewMode = interviewMode; }
    public Integer getCustomDurationMinutes() { return customDurationMinutes; }
    public void setCustomDurationMinutes(Integer customDurationMinutes) { this.customDurationMinutes = customDurationMinutes; }
    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public String getTranscriptJson() { return transcriptJson; }
    public void setTranscriptJson(String transcriptJson) { this.transcriptJson = transcriptJson; }
    public ReadinessVerdict getProposedVerdict() { return proposedVerdict; }
    public void setProposedVerdict(ReadinessVerdict proposedVerdict) { this.proposedVerdict = proposedVerdict; }
    public ReadinessVerdict getFinalVerdict() { return finalVerdict; }
    public void setFinalVerdict(ReadinessVerdict finalVerdict) { this.finalVerdict = finalVerdict; }
    public String getSignOffByUserId() { return signOffByUserId; }
    public void setSignOffByUserId(String signOffByUserId) { this.signOffByUserId = signOffByUserId; }
    public String getSignOffNote() { return signOffNote; }
    public void setSignOffNote(String signOffNote) { this.signOffNote = signOffNote; }
    public Instant getSignedOffAt() { return signedOffAt; }
    public void setSignedOffAt(Instant signedOffAt) { this.signedOffAt = signedOffAt; }
    public String getQuestionBankQuestionsJson() { return questionBankQuestionsJson; }
    public void setQuestionBankQuestionsJson(String questionBankQuestionsJson) { this.questionBankQuestionsJson = questionBankQuestionsJson; }
    public String getCustomQuestionsJson() { return customQuestionsJson; }
    public void setCustomQuestionsJson(String customQuestionsJson) { this.customQuestionsJson = customQuestionsJson; }
    public boolean isIncludeProgrammingQuestions() { return includeProgrammingQuestions; }
    public void setIncludeProgrammingQuestions(boolean includeProgrammingQuestions) {
        this.includeProgrammingQuestions = includeProgrammingQuestions;
    }
    public String getUsedQuestionIds() { return usedQuestionIds; }
    public void setUsedQuestionIds(String usedQuestionIds) { this.usedQuestionIds = usedQuestionIds; }
    public String getRecordingPath() { return recordingPath; }
    public void setRecordingPath(String recordingPath) { this.recordingPath = recordingPath; }
    public String getAssessmentStatus() { return assessmentStatus; }
    public void setAssessmentStatus(String assessmentStatus) { this.assessmentStatus = assessmentStatus; }
    public String getAssessmentError() { return assessmentError; }
    public void setAssessmentError(String assessmentError) { this.assessmentError = assessmentError; }
    public String getAssessmentResultJson() { return assessmentResultJson; }
    public void setAssessmentResultJson(String assessmentResultJson) { this.assessmentResultJson = assessmentResultJson; }
    public Long getRecordingBytes() { return recordingBytes; }
    public void setRecordingBytes(Long recordingBytes) { this.recordingBytes = recordingBytes; }
    public String getProctoringEventsJson() { return proctoringEventsJson; }
    public void setProctoringEventsJson(String proctoringEventsJson) { this.proctoringEventsJson = proctoringEventsJson; }
    public Integer getProctoringScore() { return proctoringScore; }
    public void setProctoringScore(Integer proctoringScore) { this.proctoringScore = proctoringScore; }
    public String getProctoringSnapshotsJson() { return proctoringSnapshotsJson; }
    public void setProctoringSnapshotsJson(String proctoringSnapshotsJson) { this.proctoringSnapshotsJson = proctoringSnapshotsJson; }
    public String getCandidateSource() { return candidateSource; }
    public void setCandidateSource(String candidateSource) { this.candidateSource = candidateSource; }
    public String getProctoringMode() { return proctoringMode; }
    public void setProctoringMode(String proctoringMode) { this.proctoringMode = proctoringMode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
