package com.qb.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "interview_sessions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "candidate_name", nullable = false)
    private String candidateName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String round;

    @Column(name = "interview_date")
    private LocalDate interviewDate;

    @Column(name = "interviewer_name")
    private String interviewerName;

    @Column(name = "candidate_id")
    private java.util.UUID candidateId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
