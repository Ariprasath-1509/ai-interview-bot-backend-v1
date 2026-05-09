package com.qb.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "email_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sent_by")
    private UUID sentBy;

    @Column(nullable = false)
    private String subject;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "recipient_emails", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> recipientEmails;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> filters;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}
