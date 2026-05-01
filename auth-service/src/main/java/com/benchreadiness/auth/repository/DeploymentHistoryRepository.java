package com.benchreadiness.auth.repository;

import com.benchreadiness.auth.entity.DeploymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentHistoryRepository extends JpaRepository<DeploymentHistory, String> {

    // Get all deployment history for a candidate
    List<DeploymentHistory> findByCandidateIdOrderByDeployedDateDesc(String candidateId);

    // Get active (current) deployment for a candidate
    @Query("SELECT dh FROM DeploymentHistory dh WHERE dh.candidateId = ?1 AND dh.endDate IS NULL AND dh.status = 'ACTIVE'")
    Optional<DeploymentHistory> findActiveDeploymentByCandidateId(String candidateId);

    // Get all currently active deployments
    @Query("SELECT dh FROM DeploymentHistory dh WHERE dh.endDate IS NULL AND dh.status = 'ACTIVE' ORDER BY dh.deployedDate DESC")
    List<DeploymentHistory> findAllActiveDeployments();

    // Get completed deployments
    @Query("SELECT dh FROM DeploymentHistory dh WHERE dh.status = 'COMPLETED' ORDER BY dh.endDate DESC")
    List<DeploymentHistory> findAllCompletedDeployments();
}
