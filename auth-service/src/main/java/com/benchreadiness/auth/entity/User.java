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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source", columnDefinition = "auth_svc.candidate_source")
    private CandidateSource source;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "candidate_status", columnDefinition = "auth_svc.candidate_status")
    private CandidateStatus candidateStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "rating", columnDefinition = "auth_svc.candidate_rating")
    private CandidateRating rating;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "skill_set", columnDefinition = "auth_svc.skill_set")
    private SkillSet skillSet;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "admin_source", columnDefinition = "auth_svc.admin_source")
    private AdminSource adminSource;

    @Column(name = "yoe_actual", precision = 4, scale = 1)
    private BigDecimal yoeActual;

    @Column(name = "yoe_portrayed", precision = 4, scale = 1)
    private BigDecimal yoePortrayed;

    @Column(name = "no_of_interviews")
    private Integer noOfInterviews = 0;

    @Column(name = "yop")
    private Integer yop;

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
    public CandidateSource getSource() { return source; }
    public void setSource(CandidateSource source) { this.source = source; }
    public CandidateStatus getCandidateStatus() { return candidateStatus; }
    public void setCandidateStatus(CandidateStatus candidateStatus) { this.candidateStatus = candidateStatus; }
    public CandidateRating getRating() { return rating; }
    public void setRating(CandidateRating rating) { this.rating = rating; }
    public SkillSet getSkillSet() { return skillSet; }
    public void setSkillSet(SkillSet skillSet) { this.skillSet = skillSet; }
    public BigDecimal getYoeActual() { return yoeActual; }
    public void setYoeActual(BigDecimal yoeActual) { this.yoeActual = yoeActual; }
    public BigDecimal getYoePortrayed() { return yoePortrayed; }
    public void setYoePortrayed(BigDecimal yoePortrayed) { this.yoePortrayed = yoePortrayed; }
    public Integer getNoOfInterviews() { return noOfInterviews; }
    public void setNoOfInterviews(Integer noOfInterviews) { this.noOfInterviews = noOfInterviews; }
    public Integer getYop() { return yop; }
    public void setYop(Integer yop) { this.yop = yop; }
    public AdminSource getAdminSource() { return adminSource; }
    public void setAdminSource(AdminSource adminSource) { this.adminSource = adminSource; }
}
