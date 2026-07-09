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

    /** Fullscreen exits + tab switches during Round 1, reported by the candidate's browser at submit time. */
    @Column(name = "tab_switch_count", nullable = false)
    private int tabSwitchCount = 0;

    /** True once tabSwitchCount hit the 2-strike threshold and the test paused for a proctoring violation. */
    @Column(name = "proctoring_violation", nullable = false)
    private boolean proctoringViolation = false;

    /** Set alongside proctoringViolation — blocks the candidate's link until staff explicitly permits them to continue. */
    @Column(name = "violation_locked", nullable = false)
    private boolean violationLocked = false;

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

    /** Recruiter-entered total for the live technical round, out of 35. */
    @Column(name = "round2_marks")
    private Double round2Marks;

    @Enumerated(EnumType.STRING)
    @Column(name = "round2_result", length = 32)
    private RoundDecision round2Result;

    @Column(name = "round2_recorded_by", length = 36)
    private String round2RecordedBy;

    @Column(name = "round2_recorded_at")
    private Instant round2RecordedAt;

    @Column(name = "round3_started_at")
    private Instant round3StartedAt;

    // Managerial rubric — 6 categories, 5 marks each, /30 total (computed, not stored).
    @Column(name = "round3_communication")
    private Integer round3Communication;

    @Column(name = "round3_problem_solving")
    private Integer round3ProblemSolving;

    @Column(name = "round3_attitude_coachability")
    private Integer round3AttitudeCoachability;

    @Column(name = "round3_learning_agility")
    private Integer round3LearningAgility;

    @Column(name = "round3_teamwork")
    private Integer round3Teamwork;

    @Column(name = "round3_body_language")
    private Integer round3BodyLanguage;

    @Column(name = "round3_concluding_comments", columnDefinition = "text")
    private String round3ConcludingComments;

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
    public int getTabSwitchCount() { return tabSwitchCount; }
    public void setTabSwitchCount(int tabSwitchCount) { this.tabSwitchCount = tabSwitchCount; }
    public boolean isProctoringViolation() { return proctoringViolation; }
    public void setProctoringViolation(boolean proctoringViolation) { this.proctoringViolation = proctoringViolation; }
    public boolean isViolationLocked() { return violationLocked; }
    public void setViolationLocked(boolean violationLocked) { this.violationLocked = violationLocked; }
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
    public Double getRound2Marks() { return round2Marks; }
    public void setRound2Marks(Double round2Marks) { this.round2Marks = round2Marks; }
    public RoundDecision getRound2Result() { return round2Result; }
    public void setRound2Result(RoundDecision round2Result) { this.round2Result = round2Result; }
    public String getRound2RecordedBy() { return round2RecordedBy; }
    public void setRound2RecordedBy(String round2RecordedBy) { this.round2RecordedBy = round2RecordedBy; }
    public Instant getRound2RecordedAt() { return round2RecordedAt; }
    public void setRound2RecordedAt(Instant round2RecordedAt) { this.round2RecordedAt = round2RecordedAt; }
    public Instant getRound3StartedAt() { return round3StartedAt; }
    public void setRound3StartedAt(Instant round3StartedAt) { this.round3StartedAt = round3StartedAt; }
    public Integer getRound3Communication() { return round3Communication; }
    public void setRound3Communication(Integer round3Communication) { this.round3Communication = round3Communication; }
    public Integer getRound3ProblemSolving() { return round3ProblemSolving; }
    public void setRound3ProblemSolving(Integer round3ProblemSolving) { this.round3ProblemSolving = round3ProblemSolving; }
    public Integer getRound3AttitudeCoachability() { return round3AttitudeCoachability; }
    public void setRound3AttitudeCoachability(Integer round3AttitudeCoachability) { this.round3AttitudeCoachability = round3AttitudeCoachability; }
    public Integer getRound3LearningAgility() { return round3LearningAgility; }
    public void setRound3LearningAgility(Integer round3LearningAgility) { this.round3LearningAgility = round3LearningAgility; }
    public Integer getRound3Teamwork() { return round3Teamwork; }
    public void setRound3Teamwork(Integer round3Teamwork) { this.round3Teamwork = round3Teamwork; }
    public Integer getRound3BodyLanguage() { return round3BodyLanguage; }
    public void setRound3BodyLanguage(Integer round3BodyLanguage) { this.round3BodyLanguage = round3BodyLanguage; }
    public String getRound3ConcludingComments() { return round3ConcludingComments; }
    public void setRound3ConcludingComments(String round3ConcludingComments) { this.round3ConcludingComments = round3ConcludingComments; }

    /** Sum of the 6 managerial-rubric categories, out of 30 — null until all 6 are recorded. */
    public Double getRound3Total() {
        Integer[] scores = { round3Communication, round3ProblemSolving, round3AttitudeCoachability,
                round3LearningAgility, round3Teamwork, round3BodyLanguage };
        int sum = 0;
        for (Integer s : scores) {
            if (s == null) return null;
            sum += s;
        }
        return (double) sum;
    }

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
