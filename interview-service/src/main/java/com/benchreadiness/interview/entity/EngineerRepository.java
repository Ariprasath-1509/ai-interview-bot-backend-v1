package com.benchreadiness.interview.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EngineerRepository extends JpaRepository<Engineer, String> {
    Optional<Engineer> findByUserId(String userId);
    Optional<Engineer> findByEmail(String email);
}
