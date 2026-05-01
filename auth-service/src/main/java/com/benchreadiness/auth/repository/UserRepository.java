package com.benchreadiness.auth.repository;

import com.benchreadiness.auth.entity.CandidateSource;
import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(UserRole role);
    List<User> findByRoleIn(Collection<UserRole> roles);
    
    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchCandidates(@Param("search") String search);

    List<User> findByRoleAndSourceIn(UserRole role, Collection<CandidateSource> sources);

    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND u.source IN :sources AND " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchCandidatesBySource(@Param("search") String search, @Param("sources") Collection<CandidateSource> sources);

    List<User> findByIdIn(Collection<String> ids);

    // Deployment-related queries
    Optional<User> findByOfficialEmailOrPersonalEmail(String officialEmail, String personalEmail);
    
    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND u.candidateStatus = 'DEPLOYED'")
    List<User> findDeployedCandidates();
}
