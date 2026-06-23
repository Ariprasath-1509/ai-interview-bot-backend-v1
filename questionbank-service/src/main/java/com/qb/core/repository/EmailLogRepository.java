package com.qb.core.repository;

import com.qb.core.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {

    @Query("""
            SELECT e FROM EmailLog e
            WHERE (:sentBy IS NULL OR e.sentBy = :sentBy)
              AND (:from IS NULL OR e.sentAt >= :from)
              AND (:to IS NULL OR e.sentAt <= :to)
            ORDER BY e.sentAt DESC
            """)
    Page<EmailLog> findFiltered(
            @Param("sentBy") UUID sentBy,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
