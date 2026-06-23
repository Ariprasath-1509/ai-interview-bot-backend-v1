package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, String> {}