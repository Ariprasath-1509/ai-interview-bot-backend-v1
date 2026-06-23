package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.CandidateMatchingCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CandidateMatchingCacheRepository extends JpaRepository<CandidateMatchingCache, String> {
    
    @Query("SELECT c FROM CandidateMatchingCache c WHERE c.candidateId = :candidateId AND c.expiresAt > :now")
    Optional<CandidateMatchingCache> findValidCacheByCandidate(@Param("candidateId") String candidateId, @Param("now") Instant now);
    
    @Query("SELECT c FROM CandidateMatchingCache c WHERE c.candidateEmail = :email AND c.expiresAt > :now")
    Optional<CandidateMatchingCache> findValidCacheByEmail(@Param("email") String email, @Param("now") Instant now);
    
    @Query("SELECT c FROM CandidateMatchingCache c WHERE c.expiresAt <= :now")
    List<CandidateMatchingCache> findExpiredCache(@Param("now") Instant now);
    
    void deleteByCandidateId(String candidateId);
    
    void deleteByExpiresAtBefore(Instant expiredBefore);
}