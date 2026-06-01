package com.benchreadiness.ops.compliance.repository;

import com.benchreadiness.ops.compliance.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<AuditLog> findByActorIdOrderByCreatedAtDesc(String actorId);
    List<AuditLog> findByResourceIdOrderByCreatedAtDesc(String resourceId);
}
