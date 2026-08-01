package com.benchreadiness.auth.repository;

import com.benchreadiness.auth.feature.FeatureKey;
import com.benchreadiness.auth.feature.OrganizationFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationFeatureRepository extends JpaRepository<OrganizationFeature, String> {
    List<OrganizationFeature> findByOrgCode(String orgCode);
    Optional<OrganizationFeature> findByOrgCodeAndFeatureKey(String orgCode, FeatureKey featureKey);
}
