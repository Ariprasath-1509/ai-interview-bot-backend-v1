package com.qb.core.repository;

import com.qb.core.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID>, JpaSpecificationExecutor<EmailLog> {
}
