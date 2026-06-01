package com.benchreadiness.ops.compliance.repository;

import com.benchreadiness.ops.compliance.entity.DailyTokenLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DailyTokenLimitRepository extends JpaRepository<DailyTokenLimit, String> {
    Optional<DailyTokenLimit> findByOrganizationId(String organizationId);
}
