package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.MatchingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchingResultRepository extends JpaRepository<MatchingResult, Long> {
    
    List<MatchingResult> findByCandidateIdAndExpiresAtAfter(Long candidateId, LocalDateTime now);
    
    @Query("SELECT mr FROM MatchingResult mr WHERE mr.candidateId = :candidateId " +
           "AND mr.expiresAt > :now ORDER BY mr.matchScore DESC")
    List<MatchingResult> findValidMatchesByCandidateIdOrderByScore(@Param("candidateId") Long candidateId, 
                                                                  @Param("now") LocalDateTime now);
    
    @Query("SELECT mr FROM MatchingResult mr WHERE mr.candidateId = :candidateId " +
           "AND mr.expiresAt > :now ORDER BY mr.matchScore DESC LIMIT 1")
    Optional<MatchingResult> findTopMatchByCandidateId(@Param("candidateId") Long candidateId, 
                                                      @Param("now") LocalDateTime now);
    
    void deleteByCandidateIdAndExpiresAtBefore(Long candidateId, LocalDateTime now);
}