package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "candidate_matching_cache", schema = "interview_svc")
public class CandidateMatchingCache {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "candidate_id", nullable = false, length = 36)
    private String candidateId;

    @Column(name = "candidate_email", nullable = false)
    private String candidateEmail;

    @Column(name = "matching_results_json", columnDefinition = "TEXT")
    private String matchingResultsJson;

    @Column(name = "total_clients_analyzed")
    private Integer totalClientsAnalyzed;

    @Column(name = "matching_clients_count")
    private Integer matchingClientsCount;

    @Column(name = "average_match_score")
    private Double averageMatchScore;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "cache_source", length = 20)
    private String cacheSource; // "ai-fresh", "cached"

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (computedAt == null) computedAt = Instant.now();
        if (expiresAt == null) expiresAt = Instant.now().plusSeconds(86400); // 1 day TTL
    }

    // Constructors
    public CandidateMatchingCache() {}

    public CandidateMatchingCache(String candidateId, String candidateEmail, String matchingResultsJson,
                                 Integer totalClientsAnalyzed, Integer matchingClientsCount,
                                 Double averageMatchScore, String cacheSource) {
        this.candidateId = candidateId;
        this.candidateEmail = candidateEmail;
        this.matchingResultsJson = matchingResultsJson;
        this.totalClientsAnalyzed = totalClientsAnalyzed;
        this.matchingClientsCount = matchingClientsCount;
        this.averageMatchScore = averageMatchScore;
        this.cacheSource = cacheSource;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCandidateId() { return candidateId; }
    public void setCandidateId(String candidateId) { this.candidateId = candidateId; }

    public String getCandidateEmail() { return candidateEmail; }
    public void setCandidateEmail(String candidateEmail) { this.candidateEmail = candidateEmail; }

    public String getMatchingResultsJson() { return matchingResultsJson; }
    public void setMatchingResultsJson(String matchingResultsJson) { this.matchingResultsJson = matchingResultsJson; }

    public Integer getTotalClientsAnalyzed() { return totalClientsAnalyzed; }
    public void setTotalClientsAnalyzed(Integer totalClientsAnalyzed) { this.totalClientsAnalyzed = totalClientsAnalyzed; }

    public Integer getMatchingClientsCount() { return matchingClientsCount; }
    public void setMatchingClientsCount(Integer matchingClientsCount) { this.matchingClientsCount = matchingClientsCount; }

    public Double getAverageMatchScore() { return averageMatchScore; }
    public void setAverageMatchScore(Double averageMatchScore) { this.averageMatchScore = averageMatchScore; }

    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public String getCacheSource() { return cacheSource; }
    public void setCacheSource(String cacheSource) { this.cacheSource = cacheSource; }
}