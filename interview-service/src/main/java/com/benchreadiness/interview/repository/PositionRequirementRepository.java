package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.PositionRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PositionRequirementRepository extends JpaRepository<PositionRequirement, UUID> {
}