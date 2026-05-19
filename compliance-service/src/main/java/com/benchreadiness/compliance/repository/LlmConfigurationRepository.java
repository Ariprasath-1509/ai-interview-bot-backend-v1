package com.benchreadiness.compliance.repository;

import com.benchreadiness.compliance.entity.LlmConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LlmConfigurationRepository extends JpaRepository<LlmConfiguration, Long> {
}
