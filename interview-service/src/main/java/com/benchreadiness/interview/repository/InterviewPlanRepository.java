package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.InterviewPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewPlanRepository extends JpaRepository<InterviewPlan, String> {}