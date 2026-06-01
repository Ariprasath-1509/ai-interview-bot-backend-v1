package com.benchreadiness.auth.controller;

import com.benchreadiness.auth.dto.*;
import com.benchreadiness.auth.entity.*;
import com.benchreadiness.auth.repository.UserRepository;
import com.benchreadiness.auth.service.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final Set<UserRole> STAFF_ROLES =
        Set.of(UserRole.RECRUITER, UserRole.ADMIN, UserRole.SUPER_ADMIN);

    private static final List<CandidateSource> BENCH_SOURCES = List.of(CandidateSource.BENCH, CandidateSource.B2B);
    private static final List<CandidateSource> BD_SOURCES = List.of(CandidateSource.B2B);
    private static final List<CandidateSource> RECRUITMENT_SOURCES = List.of(CandidateSource.MARKET);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ExcelParserService excelParserService;
    private final BulkImportService bulkImportService;
    private final DeploymentService deploymentService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final com.benchreadiness.auth.client.ComplianceServiceClient complianceServiceClient;
    private final CandidateBulkImportService candidateBulkImportService;

    public AuthController(UserRepository userRepository, JwtService jwtService,
                         ExcelParserService excelParserService, BulkImportService bulkImportService,
                         DeploymentService deploymentService, OtpService otpService,
                         EmailService emailService,
                         com.benchreadiness.auth.client.ComplianceServiceClient complianceServiceClient,
                         CandidateBulkImportService candidateBulkImportService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.excelParserService = excelParserService;
        this.bulkImportService = bulkImportService;
        this.deploymentService = deploymentService;
        this.otpService = otpService;
        this.emailService = emailService;
        this.complianceServiceClient = complianceServiceClient;
        this.candidateBulkImportService = candidateBulkImportService;
    }

    /** POST /auth/register — candidate self-registration */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "error", "Email already registered"));
        }
        User user = new User();
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setPassword(req.getPassword());
        user.setRole(UserRole.CANDIDATE);
        user.setContactNumber(req.getContactNumber());
        user.setOfficialEmail(req.getOfficialEmail());
        user.setPersonalEmail(req.getPersonalEmail());
        user.setBatch(req.getBatch());
        user.setSource(req.getSource());
        user.setSkillSet(req.getSkillSet());
        user.setYoeActual(req.getYoeActual());
        user.setYoePortrayed(req.getYoePortrayed());
        user.setYop(req.getYop());
        userRepository.save(user);
        
        // Log audit trail
        logAudit(user.getId(), user.getName(), "CANDIDATE", "CANDIDATE_REGISTERED", user.getId(),
            String.format("Registered: %s (%s) - %s", user.getName(), user.getEmail(), 
                user.getSource() != null ? user.getSource() : "N/A"),
            null, null);
        
        return ResponseEntity.ok(Map.of("ok", true, "message", "Registration successful. You can now log in."));
    }

    /** PATCH /auth/candidates/{id} — ADMIN updates rating, status, no_of_interviews */
    @PatchMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updateCandidate(@PathVariable String id,
                                              @RequestBody UpdateCandidateRequest req,
                                              @RequestHeader("X-User-Id") String callerId,
                                              @RequestHeader("X-User-Role") String callerRole) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (user.getRole() != UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "User is not a candidate"));
        }
        // ADMIN can only update candidates matching their source
        if (callerRole.equals("ADMIN") && !canAdminAccessCandidate(callerId, user)) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "You cannot manage candidates outside your source"));
        }
        if (req.getRating() != null) user.setRating(req.getRating());
        if (req.getCandidateStatus() != null) user.setCandidateStatus(req.getCandidateStatus());
        if (req.getNoOfInterviews() != null) user.setNoOfInterviews(req.getNoOfInterviews());
        // Extended fields — all roles
        if (req.getName() != null) user.setName(req.getName());
        if (req.getContactNumber() != null) user.setContactNumber(req.getContactNumber());
        if (req.getOfficialEmail() != null) user.setOfficialEmail(req.getOfficialEmail());
        if (req.getPersonalEmail() != null) user.setPersonalEmail(req.getPersonalEmail());
        if (req.getBatch() != null) user.setBatch(req.getBatch());
        if (req.getBatchMentor() != null) user.setBatchMentor(req.getBatchMentor());
        if (req.getSkillSet() != null) user.setSkillSet(req.getSkillSet());
        if (req.getYoeActual() != null) user.setYoeActual(req.getYoeActual());
        if (req.getYoePortrayed() != null) user.setYoePortrayed(req.getYoePortrayed());
        if (req.getYop() != null) user.setYop(req.getYop());
        if (req.getInterviewMentorName() != null) user.setInterviewMentorName(req.getInterviewMentorName());
        if (req.getClientName() != null) user.setClientName(req.getClientName());
        // Source and email — SUPER_ADMIN only
        if (callerRole.equals("SUPER_ADMIN")) {
            if (req.getSource() != null) user.setSource(req.getSource());
            if (req.getEmail() != null) user.setEmail(req.getEmail());
        }
        userRepository.save(user);

        // Log audit trail
        StringBuilder changes = new StringBuilder();
        if (req.getRating() != null) changes.append("Rating: ").append(req.getRating()).append(", ");
        if (req.getCandidateStatus() != null) changes.append("Status: ").append(req.getCandidateStatus()).append(", ");
        if (req.getNoOfInterviews() != null) changes.append("Interviews: ").append(req.getNoOfInterviews()).append(", ");
        if (req.getName() != null) changes.append("Name: ").append(req.getName()).append(", ");
        if (req.getSource() != null) changes.append("Source: ").append(req.getSource());
        
        logAudit(callerId, null, callerRole, "CANDIDATE_UPDATED", user.getId(),
            String.format("Updated %s: %s", user.getName(), changes.toString()),
            null, null);
        
        return ResponseEntity.ok(Map.of("ok", true, "message", "Candidate updated"));
    }

    /** POST /auth/staff — SUPER_ADMIN creates a staff account */
    @PostMapping("/staff")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> createStaff(@Valid @RequestBody CreateStaffRequest req,
                                          @RequestHeader("X-User-Role") String callerRole) {
        if (!STAFF_ROLES.contains(req.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Invalid staff role: " + req.getRole()));
        }
        if (req.getRole() == UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Cannot create CANDIDATE via staff endpoint"));
        }
        // ADMIN role requires adminSource
        if (req.getRole() == UserRole.ADMIN && req.getAdminSource() == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "adminSource (BENCH, BD, or RECRUITMENT) is required for ADMIN role"));
        }
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "error", "Email already registered"));
        }
        User user = new User();
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setPassword(req.getPassword());
        user.setRole(req.getRole());
        if (req.getRole() == UserRole.ADMIN) {
            user.setAdminSource(req.getAdminSource());
        }
        userRepository.save(user);
        
        // Log audit trail
        logAudit("system", "System", "SUPER_ADMIN", "STAFF_CREATED", user.getId(),
            String.format("Created %s account: %s (%s)", req.getRole(), user.getName(), user.getEmail()),
            null, null);
        
        return ResponseEntity.ok(Map.of("ok", true, "message", "Staff account created successfully"));
    }

    /** POST /auth/login — unified login for candidates and staff */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        String email = req.getUsername();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "Email not registered"));
        }
        if (user.getPassword() == null || !user.getPassword().equals(req.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "Invalid credentials"));
        }
        if (req.getRole() != null && req.getRole() != user.getRole()) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error",
                "Your account role is " + user.getRole().name() + ", not " + req.getRole().name()));
        }
        String token = jwtService.generateToken(user);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("token", token);
        response.put("role", user.getRole().name());
        response.put("name", user.getName() != null ? user.getName() : "");
        if (user.getAdminSource() != null) {
            response.put("adminSource", user.getAdminSource().name());
        }
        return ResponseEntity.ok(response);
    }

    /** POST /auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** POST /auth/forgot-password — Request OTP for password reset */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        User user = userRepository.findByEmail(req.getEmail()).orElse(null);
        if (user == null) {
            // Return error to indicate email not found (less secure but more user-friendly)
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "Email not registered. Please check your email or register first."));
        }
        
        String otp = otpService.generateOtp(req.getEmail());
        try {
            emailService.sendOtpEmail(req.getEmail(), otp, user.getName());
            logger.info("OTP sent successfully to: {}", req.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send OTP email to {}: {}", req.getEmail(), e.getMessage());
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Failed to send OTP email. Please try again later."));
        }
        
        return ResponseEntity.ok(Map.of("ok", true, "message", "OTP sent to your email. Please check your inbox."));
    }

    /** POST /auth/reset-password — Verify OTP and reset password */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        if (!otpService.validateOtp(req.getEmail(), req.getOtp())) {
            return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Invalid or expired OTP"));
        }
        User user = userRepository.findByEmail(req.getEmail()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "User not found"));
        }
        user.setPassword(req.getNewPassword());
        userRepository.save(user);
        otpService.markOtpAsUsed(req.getEmail(), req.getOtp());
        
        logAudit(user.getId(), user.getName(), user.getRole().name(), "PASSWORD_RESET", user.getId(),
            "Password reset via OTP", null, null);
        
        return ResponseEntity.ok(Map.of("ok", true, "message", "Password reset successful. You can now log in."));
    }

    /** GET /auth/users/{id} — internal service-to-service lookup */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        return userRepository.findById(id)
            .map(u -> {
                // Return full candidate profile for candidates, basic info for staff
                if (u.getRole() == UserRole.CANDIDATE) {
                    return ResponseEntity.ok(buildCandidateMap(u));
                } else {
                    return ResponseEntity.ok(buildUserMap(u));
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /** GET /auth/candidates — search registered candidates (filtered by admin source) */
    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> getCandidates(@RequestParam(required = false, defaultValue = "") String search,
                                            @RequestHeader("X-User-Id") String callerId,
                                            @RequestHeader("X-User-Role") String callerRole) {
        
        List<CandidateSource> allowedSources = getAllowedSources(callerId, callerRole);
        List<?> candidates;
        if (allowedSources == null) {
            // SUPER_ADMIN or RECRUITER — see all
            candidates = search.isBlank()
                ? userRepository.findByRole(UserRole.CANDIDATE)
                : userRepository.searchCandidates(search);
        } else {
            candidates = search.isBlank()
                ? userRepository.findByRoleAndSourceIn(UserRole.CANDIDATE, allowedSources)
                : userRepository.searchCandidatesBySource(search, allowedSources);
        }
        return ResponseEntity.ok(candidates.stream()
            .map(u -> buildCandidateMap((User) u))
            .toList());
    }

    /** GET /auth/candidates/{id} — get full candidate profile */
    @GetMapping("/candidates/{id}")
    public ResponseEntity<?> getCandidateById(@PathVariable String id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (user.getRole() != UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not a candidate"));
        }
        return ResponseEntity.ok(buildCandidateMap(user));
    }

    /** GET /auth/staff — list all staff accounts (SUPER_ADMIN only) */
    @GetMapping("/staff")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> listStaff(@RequestHeader("X-User-Role") String callerRole) {
        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        return ResponseEntity.ok(staff.stream()
            .map(u -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", u.getId());
                map.put("name", u.getName() != null ? u.getName() : "");
                map.put("email", u.getEmail());
                map.put("role", u.getRole().name());
                if (u.getAdminSource() != null) {
                    map.put("adminSource", u.getAdminSource().name());
                }
                return map;
            })
            .toList());
    }

    /** GET /auth/admins — internal endpoint for digest service */
    @GetMapping("/admins")
    public ResponseEntity<?> listAdmins() {
        List<User> admins = userRepository.findByRoleIn(List.of(UserRole.SUPER_ADMIN, UserRole.ADMIN));
        return ResponseEntity.ok(admins.stream()
            .map(u -> Map.of("email", u.getEmail(), "name", u.getName() != null ? u.getName() : ""))
            .toList());
    }

    /** GET /auth/candidates/pipeline-status — candidate pipeline analytics for daily report */
    @GetMapping("/candidates/pipeline-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getCandidatePipelineStatus() {
        List<User> candidates = userRepository.findByRole(UserRole.CANDIDATE);
        
        Map<String, Object> pipeline = new HashMap<>();
        
        // Count by status
        long rfdCount = candidates.stream().filter(c -> c.getCandidateStatus() == CandidateStatus.RFD).count();
        long wfdCount = candidates.stream().filter(c -> c.getCandidateStatus() == CandidateStatus.WFD).count();
        long dobCount = candidates.stream().filter(c -> c.getCandidateStatus() == CandidateStatus.DOB).count();
        long deployedCount = candidates.stream().filter(c -> c.getCandidateStatus() == CandidateStatus.DEPLOYED).count();
        
        pipeline.put("rfd", rfdCount);
        pipeline.put("wfd", wfdCount);
        pipeline.put("dob", dobCount);
        pipeline.put("deployed", deployedCount);
        
        // Count by source
        Map<String, Long> bySource = candidates.stream()
            .filter(c -> c.getSource() != null)
            .collect(Collectors.groupingBy(c -> c.getSource().name(), Collectors.counting()));
        pipeline.put("bySource", bySource);
        
        // Count by rating
        Map<String, Long> byRating = candidates.stream()
            .filter(c -> c.getRating() != null)
            .collect(Collectors.groupingBy(c -> c.getRating().name(), Collectors.counting()));
        pipeline.put("byRating", byRating);
        
        // Count by skill set
        Map<String, Long> bySkillSet = candidates.stream()
            .filter(c -> c.getSkillSet() != null)
            .collect(Collectors.groupingBy(c -> c.getSkillSet().name(), Collectors.counting()));
        pipeline.put("bySkillSet", bySkillSet);
        
        // Today's registrations
        Instant startOfToday = java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        long todayRegistrations = candidates.stream()
            .filter(c -> c.getCreatedAt() != null)
            .filter(c -> c.getCreatedAt().isAfter(startOfToday))
            .count();
        pipeline.put("todayRegistrations", todayRegistrations);
        
        pipeline.put("totalCandidates", candidates.size());
        
        return ResponseEntity.ok(pipeline);
    }

    /** DELETE /auth/staff/{id} — SUPER_ADMIN removes a staff account */
    @DeleteMapping("/staff/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deleteStaff(@PathVariable String id,
                                          @RequestHeader("X-User-Role") String callerRole,
                                          @RequestHeader("X-User-Id") String callerId) {
        if (id.equals(callerId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete your own account"));
        }
        User target = userRepository.findById(id).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();
        if (target.getRole() == UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete candidate accounts via this endpoint"));
        }
        
        // Log audit trail before deletion
        logAudit(callerId, null, callerRole, "STAFF_DELETED", target.getId(),
            String.format("Deleted %s account: %s (%s)", target.getRole(), target.getName(), target.getEmail()),
            null, null);
        
        userRepository.delete(target);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** GET /auth/me */
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("X-User-Id") String userId,
                                 @RequestHeader("X-User-Role") String role,
                                 @RequestHeader("X-User-Email") String email) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getRole() == UserRole.CANDIDATE) {
            return ResponseEntity.ok(buildCandidateMap(user));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", userId);
        map.put("role", role);
        map.put("email", email);
        if (user != null && user.getAdminSource() != null) {
            map.put("adminSource", user.getAdminSource().name());
        }
        return ResponseEntity.ok(map);
    }

    /** PATCH /auth/me/profile — candidate updates own editable fields */
    @PatchMapping("/me/profile")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<?> updateMyProfile(@RequestHeader("X-User-Id") String userId,
                                              @RequestHeader("X-User-Role") String callerRole,
                                              @RequestBody Map<String, Object> updates) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if (updates.containsKey("name")) user.setName((String) updates.get("name"));
        if (updates.containsKey("contactNumber")) user.setContactNumber((String) updates.get("contactNumber"));
        if (updates.containsKey("officialEmail")) user.setOfficialEmail((String) updates.get("officialEmail"));
        if (updates.containsKey("personalEmail")) user.setPersonalEmail((String) updates.get("personalEmail"));

        userRepository.save(user);
        return ResponseEntity.ok(buildCandidateMap(user));
    }

    /** PATCH /auth/candidates/{id}/resume — Update candidate resume information */
    @PatchMapping("/candidates/{id}/resume")
    public ResponseEntity<?> updateCandidateResume(@PathVariable String id,
                                                  @RequestBody Map<String, Object> resumeData) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (user.getRole() != UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "User is not a candidate"));
        }
        
        // Update resume fields
        if (resumeData.containsKey("resumeFilename")) {
            user.setResumeFilename((String) resumeData.get("resumeFilename"));
        }
        if (resumeData.containsKey("resumeFilePath")) {
            user.setResumeFilePath((String) resumeData.get("resumeFilePath"));
        }
        if (resumeData.containsKey("resumeParsedText")) {
            user.setResumeParsedText((String) resumeData.get("resumeParsedText"));
        }
        if (resumeData.containsKey("resumeSummary")) {
            user.setResumeSummary((String) resumeData.get("resumeSummary"));
        }
        if (resumeData.containsKey("resumeUploadedAt")) {
            user.setResumeUploadedAt(java.time.Instant.parse((String) resumeData.get("resumeUploadedAt")));
        }
        if (resumeData.containsKey("resumeUpdatedAt")) {
            user.setResumeUpdatedAt(java.time.Instant.parse((String) resumeData.get("resumeUpdatedAt")));
        }
        
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("ok", true, "message", "Resume information updated"));
    }

    /** POST /auth/candidates/{id}/resume — Update candidate resume information (alternative endpoint) */
    @PostMapping("/candidates/{id}/resume")
    public ResponseEntity<?> updateCandidateResumePost(@PathVariable String id,
                                                      @RequestBody Map<String, Object> resumeData) {
        return updateCandidateResume(id, resumeData);
    }

    /** POST /auth/candidates/{id}/increment-interview-count — Increment system interview count */
    @PostMapping("/candidates/{id}/increment-interview-count")
    public ResponseEntity<?> incrementSystemInterviewCount(@PathVariable String id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (user.getRole() != UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "User is not a candidate"));
        }
        
        Integer currentCount = user.getSystemInterviewCount() != null ? user.getSystemInterviewCount() : 0;
        user.setSystemInterviewCount(currentCount + 1);
        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of("ok", true, "systemInterviewCount", user.getSystemInterviewCount()));
    }

    /** POST /auth/candidates/by-email/{email}/increment-interview-count — Increment by email */
    @PostMapping("/candidates/by-email/{email}/increment-interview-count")
    public ResponseEntity<?> incrementSystemInterviewCountByEmail(@PathVariable String email) {
        // Try to find by email, officialEmail, or personalEmail
        User user = userRepository.findByOfficialEmailOrPersonalEmail(email, email).orElse(null);
        if (user == null) {
            user = userRepository.findByEmail(email).orElse(null);
        }
        if (user == null) return ResponseEntity.notFound().build();
        if (user.getRole() != UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "User is not a candidate"));
        }
        
        Integer currentCount = user.getSystemInterviewCount() != null ? user.getSystemInterviewCount() : 0;
        user.setSystemInterviewCount(currentCount + 1);
        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of("ok", true, "systemInterviewCount", user.getSystemInterviewCount()));
    }

    /** POST /auth/candidates/recalculate-system-interview-counts — Recalculate all system interview counts */
    @PostMapping("/candidates/recalculate-system-interview-counts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> recalculateSystemInterviewCounts(@RequestHeader("X-User-Role") String callerRole) {
        try {
            // This endpoint will be called by interview-service to sync counts
            // For now, just reset all counts to 0 - interview-service will provide the actual counts
            List<User> candidates = userRepository.findByRole(UserRole.CANDIDATE);
            int updatedCount = 0;
            
            for (User candidate : candidates) {
                candidate.setSystemInterviewCount(0);
                userRepository.save(candidate);
                updatedCount++;
            }
            
            return ResponseEntity.ok(Map.of(
                "ok", true, 
                "message", "System interview counts reset",
                "candidatesUpdated", updatedCount
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /** POST /auth/candidates/by-email/{email}/set-system-interview-count — Set specific count by email */
    @PostMapping("/candidates/by-email/{email}/set-system-interview-count")
    public ResponseEntity<?> setSystemInterviewCountByEmail(@PathVariable String email, @RequestBody Map<String, Integer> request) {
        // Try to find by email, officialEmail, or personalEmail
        User user = userRepository.findByOfficialEmailOrPersonalEmail(email, email).orElse(null);
        if (user == null) {
            user = userRepository.findByEmail(email).orElse(null);
        }
        if (user == null) return ResponseEntity.notFound().build();
        if (user.getRole() != UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "User is not a candidate"));
        }
        
        Integer newCount = request.get("count");
        if (newCount == null || newCount < 0) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Invalid count value"));
        }
        
        user.setSystemInterviewCount(newCount);
        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of("ok", true, "systemInterviewCount", user.getSystemInterviewCount()));
    }

    /** POST /auth/candidates/bulk-import/api — Bulk import from third-party API */
    @PostMapping("/candidates/bulk-import/api")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> bulkImportFromApi(@RequestParam String gdriveFileUrl,
                                               @RequestHeader("X-User-Id") String userId,
                                               @RequestHeader("X-User-Role") String callerRole) {
        
        if (gdriveFileUrl == null || gdriveFileUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Google Drive file URL is required"));
        }
        
        try {
            BulkImportResponse result = candidateBulkImportService.importFromThirdPartyApi(gdriveFileUrl, userId);
            
            // Log audit trail
            logAudit(userId, null, callerRole, "BULK_IMPORT_API", "CANDIDATES",
                String.format("API bulk import - Success: %d, Skipped: %d, Errors: %d", 
                    result.getSuccessCount(), result.getSkippedCount(), result.getErrorCount()),
                null, gdriveFileUrl);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("API bulk import failed for user: {}", userId, e);
            
            // Log audit trail for failure
            logAudit(userId, null, callerRole, "BULK_IMPORT_API_FAILED", "CANDIDATES",
                "API bulk import failed: " + e.getMessage(), null, gdriveFileUrl);
            
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Import failed: " + e.getMessage()));
        }
    }

    /** POST /auth/candidates/bulk-upload — Upload Excel file for bulk import validation */
    @PostMapping("/candidates/bulk-upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> uploadBulkCandidates(@RequestParam("file") MultipartFile file,
                                                 @RequestHeader("X-User-Role") String callerRole) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Please select a file to upload"));
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Only Excel (.xlsx) files are supported"));
        }

        try {
            BulkImportResponse response = excelParserService.parseExcelFile(file);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Failed to process Excel file: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Unexpected error: " + e.getMessage()));
        }
    }

    /** POST /auth/candidates/bulk-confirm/{sessionId} — Confirm and process bulk import */
    @PostMapping("/candidates/bulk-confirm/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> confirmBulkImport(@PathVariable String sessionId,
                                              @RequestHeader("X-User-Role") String callerRole) {

        try {
            BulkImportService.BulkImportResult result = bulkImportService.processBulkImport(sessionId);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", true);
            response.put("message", "Bulk import completed");
            response.put("successCount", result.getSuccessCount());
            response.put("errorCount", result.getErrorCount());
            response.put("errors", result.getErrors());
            response.put("sessionId", sessionId);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Import failed: " + e.getMessage()));
        }
    }

    /** GET /auth/candidates/bulk-download/{sessionId} — Download credentials Excel after successful import */
    @GetMapping("/candidates/bulk-download/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> downloadCredentials(@PathVariable String sessionId,
                                                @RequestHeader("X-User-Role") String callerRole) {

        try {
            // Get the stored result from session
            BulkImportService.BulkImportResult result = bulkImportService.getImportResult(sessionId);
            
            if (result == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Session not found or expired"));
            }
            
            if (result.getCreatedCandidates().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "No candidates were created in this session"));
            }

            byte[] excelBytes = bulkImportService.generateCredentialsExcel(result.getCreatedCandidates());
            
            String filename = "login_credentials_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
                
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Failed to generate credentials file: " + e.getMessage()));
        }
    }

    /** POST /auth/candidates/bulk-download — Download credentials for existing candidates */
    @PostMapping("/candidates/bulk-download")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadExistingCredentials(@RequestBody BulkDownloadRequest request,
                                                             @RequestHeader("X-User-Role") String callerRole) {

        try {
            List<User> candidates = userRepository.findByIdIn(request.getCandidateIds());
            
            if (candidates.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Generate new passwords for existing candidates and update them
            List<BulkImportService.CreatedCandidate> credentialList = new ArrayList<>();
            
            for (User user : candidates) {
                // Generate new password: FirstName@2025
                String firstName = user.getName() != null ? 
                    user.getName().split(" ")[0] : "User";
                String newPassword = firstName + "@" + LocalDateTime.now().getYear();
                
                // Update user's password (store as plain text to match registration behavior)
                user.setPassword(newPassword);
                userRepository.save(user);
                
                credentialList.add(new BulkImportService.CreatedCandidate(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    newPassword, // Show the new readable password
                    user.getSource() != null ? user.getSource().name() : "UNKNOWN",
                    user.getBatch(),
                    0
                ));
            }

            byte[] excelData = bulkImportService.generateCredentialsExcel(credentialList);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", 
                "existing_credentials_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
                
        } catch (Exception e) {
            logger.error("Error generating existing credentials file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Returns the candidate sources an admin is allowed to see.
     * BENCH admin → B2B, BENCH | RECRUITMENT admin → MARKET | SUPER_ADMIN/RECRUITER → null (all)
     */
    private List<CandidateSource> getAllowedSources(String userId, String role) {
        if (!"ADMIN".equals(role)) return null;
        User admin = userRepository.findById(userId).orElse(null);
        if (admin == null || admin.getAdminSource() == null) return null;
        return switch (admin.getAdminSource()) {
            case BENCH -> BENCH_SOURCES;
            case BD -> BD_SOURCES;
            case RECRUITMENT -> RECRUITMENT_SOURCES;
        };
    }

    private boolean canAdminAccessCandidate(String adminId, User candidate) {
        User admin = userRepository.findById(adminId).orElse(null);
        if (admin == null || admin.getAdminSource() == null) return true;
        List<CandidateSource> allowed = switch (admin.getAdminSource()) {
            case BENCH -> BENCH_SOURCES;
            case BD -> BD_SOURCES;
            case RECRUITMENT -> RECRUITMENT_SOURCES;
        };
        return candidate.getSource() == null || allowed.contains(candidate.getSource());
    }

    private Map<String, Object> buildUserMap(User u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", u.getId());
        map.put("name", u.getName() != null ? u.getName() : "");
        map.put("email", u.getEmail());
        map.put("role", u.getRole().name());
        if (u.getAdminSource() != null) {
            map.put("adminSource", u.getAdminSource().name());
        }
        return map;
    }

    private Map<String, Object> buildCandidateMap(User u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", u.getId());
        map.put("name", u.getName() != null ? u.getName() : "");
        map.put("email", u.getEmail());
        map.put("contactNumber", u.getContactNumber());
        map.put("officialEmail", u.getOfficialEmail());
        map.put("personalEmail", u.getPersonalEmail());
        map.put("batch", u.getBatch());
        map.put("source", u.getSource() != null ? u.getSource().name() : null);
        map.put("candidateStatus", u.getCandidateStatus() != null ? u.getCandidateStatus().name() : null);
        map.put("rating", u.getRating() != null ? u.getRating().name() : null);
        map.put("skillSet", u.getSkillSet() != null ? u.getSkillSet().name() : null);
        map.put("yoeActual", u.getYoeActual());
        map.put("yoePortrayed", u.getYoePortrayed());
        map.put("noOfInterviews", u.getNoOfInterviews());
        map.put("systemInterviewCount", u.getSystemInterviewCount());
        map.put("yop", u.getYop());
        
        // Resume fields
        map.put("resumeFilename", u.getResumeFilename());
        map.put("resumeFilePath", u.getResumeFilePath());
        map.put("resumeSummary", u.getResumeSummary());
        map.put("resumeUploadedAt", u.getResumeUploadedAt() != null ? u.getResumeUploadedAt().toString() : null);
        map.put("resumeUpdatedAt", u.getResumeUpdatedAt() != null ? u.getResumeUpdatedAt().toString() : null);
        
        // Deployment fields
        map.put("empId", u.getEmpId());
        map.put("deployedClientName", u.getDeployedClientName());
        map.put("deployedDate", u.getDeployedDate() != null ? u.getDeployedDate().toString() : null);
        map.put("mentor", u.getMentor());
        
        // Bulk import extended fields
        map.put("batchMentor", u.getBatchMentor());
        map.put("interviewMentorName", u.getInterviewMentorName());
        map.put("clientName", u.getClientName());
        
        return map;
    }

    /** POST /auth/candidates/deployment/bulk-import — Bulk import deployment data */
    @PostMapping("/candidates/deployment/bulk-import")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> bulkImportDeployments(@RequestParam("file") MultipartFile file,
                                                   @RequestHeader("X-User-Role") String callerRole) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Please select a file to upload"));
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Only Excel (.xlsx) files are supported"));
        }

        try {
            Map<String, Object> result = deploymentService.bulkImportDeployments(file);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Failed to process Excel file: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Unexpected error: " + e.getMessage()));
        }
    }

    /** GET /auth/candidates/deployed — List only deployed candidates */
    @GetMapping("/candidates/deployed")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> getDeployedCandidates(@RequestHeader("X-User-Role") String callerRole) {
        
        List<User> deployed = deploymentService.getDeployedCandidates();
        return ResponseEntity.ok(deployed.stream()
            .map(this::buildCandidateMap)
            .toList());
    }

    /** PATCH /auth/candidates/{id}/deployment — Update deployment fields */
    @PatchMapping("/candidates/{id}/deployment")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updateDeployment(@PathVariable String id,
                                              @RequestBody Map<String, Object> deploymentData,
                                              @RequestHeader("X-User-Role") String callerRole) {

        try {
            String empId = (String) deploymentData.get("empId");
            String clientName = (String) deploymentData.get("clientName");
            java.time.LocalDate deployedDate = deploymentData.get("deployedDate") != null 
                ? java.time.LocalDate.parse((String) deploymentData.get("deployedDate")) 
                : null;
            String mentor = (String) deploymentData.get("mentor");

            User updated = deploymentService.updateDeployment(id, empId, clientName, deployedDate, mentor);
            return ResponseEntity.ok(buildCandidateMap(updated));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /** DELETE /auth/candidates/{id}/deployment — Clear deployment fields */
    @DeleteMapping("/candidates/{id}/deployment")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> clearDeployment(@PathVariable String id,
                                             @RequestHeader("X-User-Role") String callerRole) {

        try {
            User updated = deploymentService.clearDeployment(id);
            return ResponseEntity.ok(buildCandidateMap(updated));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /** GET /auth/candidates/{id}/deployment-history — Get deployment history for a candidate */
    @GetMapping("/candidates/{id}/deployment-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> getDeploymentHistory(@PathVariable String id,
                                                  @RequestHeader("X-User-Role") String callerRole) {

        logger.info("Fetching deployment history for candidate ID: {}", id);
        List<DeploymentHistory> history = deploymentService.getDeploymentHistory(id);
        logger.info("Found {} deployment history records for candidate ID: {}", history.size(), id);
        
        return ResponseEntity.ok(history.stream()
            .map(this::buildDeploymentHistoryMap)
            .toList());
    }

    /** POST /auth/candidates/{id}/end-deployment — End current deployment and move back to B2B */
    @PostMapping("/candidates/{id}/end-deployment")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> endDeployment(@PathVariable String id,
                                          @RequestBody(required = false) Map<String, Object> requestBody,
                                          @RequestHeader("X-User-Role") String callerRole) {

        try {
            java.time.LocalDate endDate = null;
            if (requestBody != null && requestBody.containsKey("endDate")) {
                endDate = java.time.LocalDate.parse((String) requestBody.get("endDate"));
            }
            
            User updated = deploymentService.endDeployment(id, endDate);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", "Deployment ended successfully",
                "candidate", buildCandidateMap(updated)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /** GET /auth/deployment-history — Get all deployment history */
    @GetMapping("/deployment-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> getAllDeploymentHistory(@RequestParam(required = false) String status,
                                                     @RequestHeader("X-User-Role") String callerRole) {

        List<DeploymentHistory> history;
        if ("ACTIVE".equalsIgnoreCase(status)) {
            history = deploymentService.getAllActiveDeployments();
        } else if ("COMPLETED".equalsIgnoreCase(status)) {
            history = deploymentService.getAllCompletedDeployments();
        } else {
            // Return all if no status filter
            history = deploymentService.getAllActiveDeployments();
        }

        return ResponseEntity.ok(history.stream()
            .map(this::buildDeploymentHistoryMap)
            .toList());
    }

    private Map<String, Object> buildDeploymentHistoryMap(DeploymentHistory dh) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", dh.getId());
        map.put("candidateId", dh.getCandidateId());
        map.put("empId", dh.getEmpId());
        map.put("clientName", dh.getClientName());
        map.put("deployedDate", dh.getDeployedDate() != null ? dh.getDeployedDate().toString() : null);
        map.put("endDate", dh.getEndDate() != null ? dh.getEndDate().toString() : null);
        map.put("mentor", dh.getMentor());
        map.put("status", dh.getStatus());
        map.put("createdAt", dh.getCreatedAt() != null ? dh.getCreatedAt().toString() : null);
        map.put("updatedAt", dh.getUpdatedAt() != null ? dh.getUpdatedAt().toString() : null);
        
        // Add candidate info
        userRepository.findById(dh.getCandidateId()).ifPresent(candidate -> {
            map.put("candidateName", candidate.getName());
            map.put("candidateEmail", candidate.getEmail());
        });
        
        return map;
    }

    private void logAudit(String actorId, String actorName, String actorRole, String action,
                         String resourceId, String detail, String oldValue, String newValue) {
        try {
            Map<String, Object> auditLog = new HashMap<>();
            auditLog.put("actorId", actorId);
            if (actorName != null) auditLog.put("actorName", actorName);
            auditLog.put("actorRole", actorRole);
            auditLog.put("action", action);
            auditLog.put("resource", "CANDIDATE");
            auditLog.put("resourceId", resourceId);
            if (detail != null) auditLog.put("detail", detail);
            if (oldValue != null) auditLog.put("oldValue", oldValue);
            if (newValue != null) auditLog.put("newValue", newValue);
            auditLog.put("ipAddress", resolveClientIp());
            complianceServiceClient.recordAuditLog(auditLog);
        } catch (Exception e) {
            logger.error("Failed to record audit log: {}", e.getMessage());
        }
    }

    private String resolveClientIp() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                (org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "internal";
            jakarta.servlet.http.HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isBlank()) return ip.trim();
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
