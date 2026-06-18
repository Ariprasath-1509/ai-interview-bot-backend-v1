package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.Engineer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EngineerRepository extends JpaRepository<Engineer, String> {
    Optional<Engineer> findByUserId(String userId);
    Optional<Engineer> findByEmail(String email);

    @Query(value = """
            SELECT DISTINCT ON (e.id) e.id AS engineer_id, u.branch AS branch
            FROM interview_svc.engineers e
            JOIN auth_svc.users u ON (
                (u.id = e.user_id AND u.role = 'CANDIDATE')
                OR (LOWER(u.email) = LOWER(e.email) AND u.role = 'CANDIDATE')
            )
            WHERE e.id IN (:engineerIds)
              AND u.branch IS NOT NULL
            ORDER BY e.id, CASE WHEN u.id = e.user_id THEN 0 ELSE 1 END
            """, nativeQuery = true)
    List<Object[]> findCandidateBranchesByEngineerIds(@Param("engineerIds") Collection<String> engineerIds);
}