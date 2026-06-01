package com.benchreadiness.ops.compliance.repository;

import com.benchreadiness.ops.compliance.entity.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, String> {
    Optional<RetentionPolicy> findByRegion(String region);
}
