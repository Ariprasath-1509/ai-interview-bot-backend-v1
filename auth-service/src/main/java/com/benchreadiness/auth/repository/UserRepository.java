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
    List<User> findByRole(UserRole role);
    List<User> findByRoleIn(Collection<UserRole> roles);
    
    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchCandidates(@Param("search") String search);

    List<User> findByRoleAndSourceIn(UserRole role, Collection<String> sources);

    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND u.source IN :sources AND " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchCandidatesBySource(@Param("search") String search, @Param("sources") Collection<String> sources);

    List<User> findByIdIn(Collection<String> ids);

    // Deployment-related queries
    Optional<User> findByOfficialEmailOrPersonalEmail(String officialEmail, String personalEmail);
    Optional<User> findByEmpId(String empId);
    
    @Query("SELECT u FROM User u WHERE u.role = 'CANDIDATE' AND u.candidateStatus = 'DEPLOYED'")
    List<User> findDeployedCandidates();
    
    // Bulk import duplicate check
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE " +
           "u.email = :email1 OR u.email = :email2 OR " +
           "u.officialEmail = :email1 OR u.officialEmail = :email2 OR " +
           "u.personalEmail = :email1 OR u.personalEmail = :email2 OR " +
           "u.contactNumber = :contactNumber")
    boolean existsByEmailOrOfficialEmailOrPersonalEmailOrContactNumber(
        @Param("email1") String email1, 
        @Param("email2") String email2, 
        @Param("email1") String officialEmail1, 
        @Param("email2") String officialEmail2, 
        @Param("contactNumber") String contactNumber);
}
