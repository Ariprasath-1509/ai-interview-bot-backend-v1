package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.SkillRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SkillRequirementRepository extends JpaRepository<SkillRequirement, UUID> {
}