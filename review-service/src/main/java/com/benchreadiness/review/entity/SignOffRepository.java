package com.benchreadiness.review.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SignOffRepository extends JpaRepository<SignOff, String> {
    Optional<SignOff> findByInterviewId(String interviewId);
}
