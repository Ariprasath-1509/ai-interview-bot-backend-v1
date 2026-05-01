package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    
    Optional<Client> findByClientName(String clientName);
    
    List<Client> findByStatus(Client.ClientStatus status);
    
    @Query("SELECT c FROM Client c ORDER BY c.createdAt DESC")
    List<Client> findAllOrderByCreatedAtDesc();
    
    boolean existsByClientName(String clientName);
    
    // New methods for pending clients
    List<Client> findByBenchReviewedFalseAndBenchB2bCandidatesNeededGreaterThan(Integer minCandidates);
    
    List<Client> findByRecruitmentReviewedFalseAndMarketCandidatesNeededGreaterThan(Integer minCandidates);
    
    @Query("SELECT c FROM Client c WHERE c.benchReviewed = false AND c.benchB2bCandidatesNeeded > 0 ORDER BY c.createdAt ASC")
    List<Client> findPendingBenchClients();
    
    @Query("SELECT c FROM Client c WHERE c.recruitmentReviewed = false AND c.marketCandidatesNeeded > 0 ORDER BY c.createdAt ASC")
    List<Client> findPendingRecruitmentClients();
}