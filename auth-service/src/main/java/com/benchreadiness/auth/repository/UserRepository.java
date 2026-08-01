package com.benchreadiness.auth.repository;

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
    Optional<User> findByEmailIgnoreCase(String email);
    List<User> findByRole(UserRole role);
    List<User> findByRoleIn(Collection<UserRole> roles);
    
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByOrgCodeIgnoreCase(String orgCode);

    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND (" +
           "LOWER(u.email) = LOWER(:email) OR " +
           "LOWER(COALESCE(u.officialEmail, '')) = LOWER(:email) OR " +
           "LOWER(COALESCE(u.personalEmail, '')) = LOWER(:email))")
    List<User> findCandidatesByAnyEmailIgnoreCase(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.role = 'CANDIDATE' AND (" +
           "(:officialEmail IS NOT NULL AND (" +
           "LOWER(u.email) = LOWER(:officialEmail) OR " +
           "LOWER(COALESCE(u.officialEmail, '')) = LOWER(:officialEmail) OR " +
           "LOWER(COALESCE(u.personalEmail, '')) = LOWER(:officialEmail))) OR " +
           "(:personalEmail IS NOT NULL AND (" +
           "LOWER(u.email) = LOWER(:personalEmail) OR " +
           "LOWER(COALESCE(u.officialEmail, '')) = LOWER(:personalEmail) OR " +
           "LOWER(COALESCE(u.personalEmail, '')) = LOWER(:personalEmail))) OR " +
           "(:contactNumber IS NOT NULL AND u.contactNumber = :contactNumber))")
    boolean existsCandidateByEmailsOrContact(
        @Param("officialEmail") String officialEmail,
        @Param("personalEmail") String personalEmail,
        @Param("contactNumber") String contactNumber);

    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchCandidates(@Param("search") String search);

    List<User> findByRoleAndBranch(UserRole role, String branch);

    @Query("SELECT u FROM User u WHERE u.role = :role AND u.branch = :branch AND "
           + "(u.source IN :sources OR (:includeNullSource = true AND u.source IS NULL))")
    List<User> findCandidatesByBranchAndSources(@Param("role") UserRole role,
                                                @Param("branch") String branch,
                                                @Param("sources") Collection<String> sources,
                                                @Param("includeNullSource") boolean includeNullSource);

    @Query("SELECT u FROM User u WHERE u.role = :role AND "
           + "(u.source IN :sources OR (:includeNullSource = true AND u.source IS NULL))")
    List<User> findCandidatesBySources(@Param("role") UserRole role,
                                         @Param("sources") Collection<String> sources,
                                         @Param("includeNullSource") boolean includeNullSource);

    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND u.branch = :branch AND " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchCandidatesByBranch(@Param("search") String search, @Param("branch") String branch);

    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND u.branch = :branch AND "
           + "(u.source IN :sources OR (:includeNullSource = true AND u.source IS NULL)) AND "
           + "(LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR "
           + "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchCandidatesByBranchAndSources(@Param("search") String search,
                                                    @Param("branch") String branch,
                                                    @Param("sources") Collection<String> sources,
                                                    @Param("includeNullSource") boolean includeNullSource);

    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND "
           + "(u.source IN :sources OR (:includeNullSource = true AND u.source IS NULL)) AND "
           + "(LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR "
           + "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchCandidatesBySources(@Param("search") String search,
                                           @Param("sources") Collection<String> sources,
                                                    @Param("includeNullSource") boolean includeNullSource);

    List<User> findByIdIn(Collection<String> ids);

    long countByRoleAndOrgCode(UserRole role, String orgCode);

    // Deployment-related queries
    Optional<User> findByOfficialEmailOrPersonalEmail(String officialEmail, String personalEmail);
    Optional<User> findByEmpId(String empId);
    
    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND u.candidateStatus = 'DEPLOYED'")
    List<User> findDeployedCandidates();
}
