package com.benchreadiness.screening.entity;

import com.benchreadiness.screening.entity.enums.CandidateStage;
import com.benchreadiness.screening.entity.enums.RoundDecision;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "screening_candidates", schema = "screening_svc")
public class ScreeningCandidate {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private ScreeningBatch batch;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "contact_number", length = 32)
    private String contactNumber;

    /** JSPIDERS or QSPIDERS — which institute brand the candidate is from. */
    @Column(length = 32)
    private String institute;

    /** The physical location/branch of that institute, e.g. "Bangalore", "Chennai". */
    @Column(length = 64)
    private String branch;

    @Column(name = "yop")
    private Integer yop;

    /** Years of experience. */
    @Column(name = "experience")
    private Double experience;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "shuffle_seed", nullable = false)
    private long shuffleSeed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CandidateStage stage = CandidateStage.ROUND1_PENDING;

    @Column(name = "round1_score")
    private Double round1Score;

    @Column(name = "round1_started_at")
    private Instant round1StartedAt;

    @Column(name = "round1_submitted_at")
    private Instant round1SubmittedAt;

    /** Staff override — lets this candidate reopen/submit Round 1 even after the batch deadline has passed. */
    @Column(name = "allow_late_submission", nullable = false)
    private boolean allowLateSubmission = false;

    @Column(name = "round2_started_at")
    private Instant round2StartedAt;

    @Column(name = "round2_strengths", columnDefinition = "text")
    private String round2Strengths;

    @Column(name = "round2_weaknesses", columnDefinition = "text")
    private String round2Weaknesses;

    @Column(name = "round2_practical", columnDefinition = "text")
    private String round2Practical;

    @Column(name = "round2_improvements", columnDefinition = "text")
    private String round2Improvements;

    @Enumerated(EnumType.STRING)
    @Column(name = "round2_result", length = 32)
    private RoundDecision round2Result;

    @Column(name = "round2_recorded_by", length = 36)
    private String round2RecordedBy;

    @Column(name = "round2_recorded_at")
    private Instant round2RecordedAt;

    @Column(name = "round3_started_at")
    private Instant round3StartedAt;

    @Column(name = "round3_strengths", columnDefinition = "text")
    private String round3Strengths;

    @Column(name = "round3_weaknesses", columnDefinition = "text")
    private String round3Weaknesses;

    @Column(name = "round3_practical", columnDefinition = "text")
    private String round3Practical;

    @Column(name = "round3_improvements", columnDefinition = "text")
    private String round3Improvements;

    @Enumerated(EnumType.STRING)
    @Column(name = "round3_result", length = 32)
    private RoundDecision round3Result;

    @Column(name = "round3_recorded_by", length = 36)
    private String round3RecordedBy;

    @Column(name = "round3_recorded_at")
    private Instant round3RecordedAt;

    @Column(name = "converted_user_id", length = 36)
    private String convertedUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public ScreeningBatch getBatch() { return batch; }
    public void setBatch(ScreeningBatch batch) { this.batch = batch; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getInstitute() { return institute; }
    public void setInstitute(String institute) { this.institute = institute; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public Integer getYop() { return yop; }
    public void setYop(Integer yop) { this.yop = yop; }
    public Double getExperience() { return experience; }
    public void setExperience(Double experience) { this.experience = experience; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public long getShuffleSeed() { return shuffleSeed; }
    public void setShuffleSeed(long shuffleSeed) { this.shuffleSeed = shuffleSeed; }
    public CandidateStage getStage() { return stage; }
    public void setStage(CandidateStage stage) { this.stage = stage; }
    public Double getRound1Score() { return round1Score; }
    public void setRound1Score(Double round1Score) { this.round1Score = round1Score; }
    public Instant getRound1StartedAt() { return round1StartedAt; }
    public void setRound1StartedAt(Instant round1StartedAt) { this.round1StartedAt = round1StartedAt; }
    public Instant getRound1SubmittedAt() { return round1SubmittedAt; }
    public void setRound1SubmittedAt(Instant round1SubmittedAt) { this.round1SubmittedAt = round1SubmittedAt; }
    public boolean isAllowLateSubmission() { return allowLateSubmission; }
    public void setAllowLateSubmission(boolean allowLateSubmission) { this.allowLateSubmission = allowLateSubmission; }
    public Instant getRound2StartedAt() { return round2StartedAt; }
    public void setRound2StartedAt(Instant round2StartedAt) { this.round2StartedAt = round2StartedAt; }
    public String getRound2Strengths() { return round2Strengths; }
    public void setRound2Strengths(String round2Strengths) { this.round2Strengths = round2Strengths; }
    public String getRound2Weaknesses() { return round2Weaknesses; }
    public void setRound2Weaknesses(String round2Weaknesses) { this.round2Weaknesses = round2Weaknesses; }
    public String getRound2Practical() { return round2Practical; }
    public void setRound2Practical(String round2Practical) { this.round2Practical = round2Practical; }
    public String getRound2Improvements() { return round2Improvements; }
    public void setRound2Improvements(String round2Improvements) { this.round2Improvements = round2Improvements; }
    public RoundDecision getRound2Result() { return round2Result; }
    public void setRound2Result(RoundDecision round2Result) { this.round2Result = round2Result; }
    public String getRound2RecordedBy() { return round2RecordedBy; }
    public void setRound2RecordedBy(String round2RecordedBy) { this.round2RecordedBy = round2RecordedBy; }
    public Instant getRound2RecordedAt() { return round2RecordedAt; }
    public void setRound2RecordedAt(Instant round2RecordedAt) { this.round2RecordedAt = round2RecordedAt; }
    public Instant getRound3StartedAt() { return round3StartedAt; }
    public void setRound3StartedAt(Instant round3StartedAt) { this.round3StartedAt = round3StartedAt; }
    public String getRound3Strengths() { return round3Strengths; }
    public void setRound3Strengths(String round3Strengths) { this.round3Strengths = round3Strengths; }
    public String getRound3Weaknesses() { return round3Weaknesses; }
    public void setRound3Weaknesses(String round3Weaknesses) { this.round3Weaknesses = round3Weaknesses; }
    public String getRound3Practical() { return round3Practical; }
    public void setRound3Practical(String round3Practical) { this.round3Practical = round3Practical; }
    public String getRound3Improvements() { return round3Improvements; }
    public void setRound3Improvements(String round3Improvements) { this.round3Improvements = round3Improvements; }
    public RoundDecision getRound3Result() { return round3Result; }
    public void setRound3Result(RoundDecision round3Result) { this.round3Result = round3Result; }
    public String getRound3RecordedBy() { return round3RecordedBy; }
    public void setRound3RecordedBy(String round3RecordedBy) { this.round3RecordedBy = round3RecordedBy; }
    public Instant getRound3RecordedAt() { return round3RecordedAt; }
    public void setRound3RecordedAt(Instant round3RecordedAt) { this.round3RecordedAt = round3RecordedAt; }
    public String getConvertedUserId() { return convertedUserId; }
    public void setConvertedUserId(String convertedUserId) { this.convertedUserId = convertedUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
