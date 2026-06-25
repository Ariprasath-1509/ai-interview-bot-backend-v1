package com.benchreadiness.auth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "users", schema = "auth_svc")
public class User {

    @Id
    @Column(length = 36)
    private String id;

    @Column(unique = true)
    private String email;

    private String name;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "auth_svc.user_role")
    private UserRole role = UserRole.CANDIDATE;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(name = "official_email")
    private String officialEmail;

    @Column(name = "personal_email")
    private String personalEmail;

    @Column(name = "batch", length = 100)
    private String batch;

    @Column(name = "source", length = 128)
    private String source;

    @Column(name = "candidate_status", length = 128)
    private String candidateStatus;

    @Column(name = "rating", length = 128)
    private String rating;

    @Column(name = "skill_set", length = 128)
    private String skillSet;

    @Column(name = "admin_source", length = 128)
    private String adminSource;

    @Column(name = "branch", length = 32, nullable = false)
    private String branch = "DEVELOPMENT";

    @Column(name = "yoe_actual", precision = 4, scale = 1)
    private BigDecimal yoeActual;

    @Column(name = "yoe_portrayed", precision = 4, scale = 1)
    private BigDecimal yoePortrayed;

    @Column(name = "no_of_interviews")
    private Integer noOfInterviews = 0;

    @Column(name = "system_interview_count")
    private Integer systemInterviewCount = 0;

    @Column(name = "yop")
    private Integer yop;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Resume-related fields
    @Column(name = "resume_filename")
    private String resumeFilename;

    @Column(name = "resume_file_path", length = 500)
    private String resumeFilePath;

    @Column(name = "resume_parsed_text", columnDefinition = "TEXT")
    private String resumeParsedText;

    @Column(name = "resume_summary", columnDefinition = "TEXT")
    private String resumeSummary;

    @Column(name = "resume_uploaded_at")
    private Instant resumeUploadedAt;

    @Column(name = "resume_updated_at")
    private Instant resumeUpdatedAt;

    @Column(name = "batch_mentor")
    private String batchMentor;

    @Column(name = "interview_mentor_name")
    private String interviewMentorName;

    @Column(name = "client_name")
    private String clientName;

    // Deployment fields
    @Column(name = "emp_id", length = 50, unique = true)
    private String empId;

    @Column(name = "deployed_client_name")
    private String deployedClientName;

    @Column(name = "deployed_date")
    private java.time.LocalDate deployedDate;

    @Column(name = "mentor")
    private String mentor;

    /** When false, login is rejected. Used to gate market candidates between interview schedules. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public String getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getOfficialEmail() { return officialEmail; }
    public void setOfficialEmail(String officialEmail) { this.officialEmail = officialEmail; }
    public String getPersonalEmail() { return personalEmail; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCandidateStatus() { return candidateStatus; }
    public void setCandidateStatus(String candidateStatus) { this.candidateStatus = candidateStatus; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getSkillSet() { return skillSet; }
    public void setSkillSet(String skillSet) { this.skillSet = skillSet; }
    public BigDecimal getYoeActual() { return yoeActual; }
    public void setYoeActual(BigDecimal yoeActual) { this.yoeActual = yoeActual; }
    public BigDecimal getYoePortrayed() { return yoePortrayed; }
    public void setYoePortrayed(BigDecimal yoePortrayed) { this.yoePortrayed = yoePortrayed; }
    public Integer getNoOfInterviews() { return noOfInterviews; }
    public void setNoOfInterviews(Integer noOfInterviews) { this.noOfInterviews = noOfInterviews; }
    public Integer getSystemInterviewCount() { return systemInterviewCount; }
    public void setSystemInterviewCount(Integer systemInterviewCount) { this.systemInterviewCount = systemInterviewCount; }
    public Integer getYop() { return yop; }
    public void setYop(Integer yop) { this.yop = yop; }
    public String getAdminSource() { return adminSource; }
    public void setAdminSource(String adminSource) { this.adminSource = adminSource; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    // Resume getters and setters
    public String getResumeFilename() { return resumeFilename; }
    public void setResumeFilename(String resumeFilename) { this.resumeFilename = resumeFilename; }
    public String getResumeFilePath() { return resumeFilePath; }
    public void setResumeFilePath(String resumeFilePath) { this.resumeFilePath = resumeFilePath; }
    public String getResumeParsedText() { return resumeParsedText; }
    public void setResumeParsedText(String resumeParsedText) { this.resumeParsedText = resumeParsedText; }
    public String getResumeSummary() { return resumeSummary; }
    public void setResumeSummary(String resumeSummary) { this.resumeSummary = resumeSummary; }
    public Instant getResumeUploadedAt() { return resumeUploadedAt; }
    public void setResumeUploadedAt(Instant resumeUploadedAt) { this.resumeUploadedAt = resumeUploadedAt; }
    public Instant getResumeUpdatedAt() { return resumeUpdatedAt; }
    public void setResumeUpdatedAt(Instant resumeUpdatedAt) { this.resumeUpdatedAt = resumeUpdatedAt; }

    // Deployment getters and setters
    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }
    public String getDeployedClientName() { return deployedClientName; }
    public void setDeployedClientName(String deployedClientName) { this.deployedClientName = deployedClientName; }
    public java.time.LocalDate getDeployedDate() { return deployedDate; }
    public void setDeployedDate(java.time.LocalDate deployedDate) { this.deployedDate = deployedDate; }
    public String getMentor() { return mentor; }
    public void setMentor(String mentor) { this.mentor = mentor; }
    public String getBatchMentor() { return batchMentor; }
    public void setBatchMentor(String batchMentor) { this.batchMentor = batchMentor; }
    public String getInterviewMentorName() { return interviewMentorName; }
    public void setInterviewMentorName(String interviewMentorName) { this.interviewMentorName = interviewMentorName; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    // Timestamp getters and setters
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
