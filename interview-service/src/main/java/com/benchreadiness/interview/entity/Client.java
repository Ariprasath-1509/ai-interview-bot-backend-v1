package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clients", schema = "interview_svc")
public class Client {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false)
    private String clientName;
    
    @Column(nullable = false)
    private String jdRole;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String jdDescription;
    
    @Column(nullable = false)
    private Integer positionsVacant;
    
    @Column(nullable = false)
    private Integer marketCandidatesNeeded;
    
    @Column(name = "bench_b2b_candidates_needed", nullable = false)
    private Integer benchB2bCandidatesNeeded;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientStatus status;
    
    @Column(nullable = false)
    private Boolean benchReviewed = false;
    
    @Column(nullable = false)
    private Boolean recruitmentReviewed = false;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "doc_id")
    private String docId;
    
    @Column(name = "jd_file")
    @JdbcTypeCode(SqlTypes.BINARY)
    private byte[] jdFile;
    
    @Column(name = "jd_file_name")
    private String jdFileName;
    
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<SkillRequirement> skillRequirements = new ArrayList<>();
    
    // Constructors
    public Client() {}
    
    public Client(String clientName, String jdRole, String jdDescription, 
                 Integer positionsVacant, Integer marketCandidatesNeeded, 
                 Integer benchB2bCandidatesNeeded, ClientStatus status) {
        this.clientName = clientName;
        this.jdRole = jdRole;
        this.jdDescription = jdDescription;
        this.positionsVacant = positionsVacant;
        this.marketCandidatesNeeded = marketCandidatesNeeded;
        this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded;
        this.status = status;
        this.benchReviewed = false;
        this.recruitmentReviewed = false;
    }
    
    // Getters
    public UUID getId() {
        return id;
    }
    
    public String getClientName() {
        return clientName;
    }
    
    public String getJdRole() {
        return jdRole;
    }
    
    public String getJdDescription() {
        return jdDescription;
    }
    
    public Integer getPositionsVacant() {
        return positionsVacant;
    }
    
    public Integer getMarketCandidatesNeeded() {
        return marketCandidatesNeeded;
    }
    
    public Integer getBenchB2bCandidatesNeeded() {
        return benchB2bCandidatesNeeded;
    }
    
    public ClientStatus getStatus() {
        return status;
    }
    
    public Boolean getBenchReviewed() {
        return benchReviewed;
    }
    
    public Boolean getRecruitmentReviewed() {
        return recruitmentReviewed;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public String getDocId() {
        return docId;
    }
    
    public byte[] getJdFile() {
        return jdFile;
    }
    
    public String getJdFileName() {
        return jdFileName;
    }
    
    // Setters
    public void setId(UUID id) {
        this.id = id;
    }
    
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
    
    public void setJdRole(String jdRole) {
        this.jdRole = jdRole;
    }
    
    public void setJdDescription(String jdDescription) {
        this.jdDescription = jdDescription;
    }
    
    public void setPositionsVacant(Integer positionsVacant) {
        this.positionsVacant = positionsVacant;
    }
    
    public void setMarketCandidatesNeeded(Integer marketCandidatesNeeded) {
        this.marketCandidatesNeeded = marketCandidatesNeeded;
    }
    
    public void setBenchB2bCandidatesNeeded(Integer benchB2bCandidatesNeeded) {
        this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded;
    }
    
    public void setStatus(ClientStatus status) {
        this.status = status;
    }
    
    public void setBenchReviewed(Boolean benchReviewed) {
        this.benchReviewed = benchReviewed;
    }
    
    public void setRecruitmentReviewed(Boolean recruitmentReviewed) {
        this.recruitmentReviewed = recruitmentReviewed;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public void setDocId(String docId) {
        this.docId = docId;
    }
    
    public void setJdFile(byte[] jdFile) {
        this.jdFile = jdFile;
    }
    
    public void setJdFileName(String jdFileName) {
        this.jdFileName = jdFileName;
    }
    
    public List<SkillRequirement> getSkillRequirements() {
        return skillRequirements;
    }
    
    public void setSkillRequirements(List<SkillRequirement> skillRequirements) {
        this.skillRequirements = skillRequirements;
    }
    
    public void addSkillRequirement(SkillRequirement skillRequirement) {
        skillRequirements.add(skillRequirement);
        skillRequirement.setClient(this);
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum ClientStatus {
        ACTIVE, INACTIVE
    }
}