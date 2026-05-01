package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    
    Optional<Candidate> findByEmail(String email);
    
    @Query("SELECT c FROM Candidate c WHERE c.id = :id")
    Optional<Candidate> findByIdWithSkills(@Param("id") Long id);
}