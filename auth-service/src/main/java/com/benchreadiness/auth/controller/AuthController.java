package com.benchreadiness.auth.controller;

import com.benchreadiness.auth.branch.Branch;
import com.benchreadiness.auth.branch.BranchAccess;
import com.benchreadiness.auth.dto.*;
import com.benchreadiness.auth.entity.DeploymentHistory;
import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.entity.UserRole;
import com.benchreadiness.auth.repository.UserRepository;
import com.benchreadiness.auth.masterdata.MasterDataCategory;
import com.benchreadiness.auth.masterdata.MasterDataService;
import com.benchreadiness.auth.service.*;
import com.benchreadiness.auth.util.PiiRedactor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "Login, registration, token lifecycle, and password reset")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final Set<UserRole> STAFF_ROLES =
        Set.of(UserRole.RECRUITER, UserRole.TESTING_RECRUITER,
               UserRole.ADMIN, UserRole.TESTING_ADMIN, UserRole.SUPER_ADMIN);

    private static final String STAFF_READ_ROLES =
        "ADMIN', 'TESTING_ADMIN', 'SUPER_ADMIN', 'RECRUITER', 'TESTING_RECRUITER";

    private static final String STAFF_ADMIN_ROLES =
        "ADMIN', 'TESTING_ADMIN', 'SUPER_ADMIN";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final MasterDataService masterDataService;
    private final ExcelParserService excelParserService;
    private final BulkImportService bulkImportService;
    private final DeploymentService deploymentService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final CandidateBulkImportService candidateBulkImportService;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    public AuthController(UserRepository userRepository, JwtService jwtService,
                         MasterDataService masterDataService,
                         ExcelParserService excelParserService, BulkImportService bulkImportService,
                         DeploymentService deploymentService, OtpService otpService,
                         EmailService emailService,
                         CandidateBulkImportService candidateBulkImportService,
                         PasswordService passwordService,
                         RefreshTokenService refreshTokenService,
                         LoginAttemptService loginAttemptService,
                         AuditService auditService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.masterDataService = masterDataService;
        this.excelParserService = excelParserService;
        this.bulkImportService = bulkImportService;
        this.deploymentService = deploymentService;
        this.otpService = otpService;
        this.emailService = emailService;
        this.candidateBulkImportService = candidateBulkImportService;
        this.passwordService = passwordService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
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
        user.setPassword(passwordService.encode(req.getPassword()));
        user.setRole(UserRole.CANDIDATE);
        user.setContactNumber(req.getContactNumber());
        user.setOfficialEmail(req.getOfficialEmail());
        user.setPersonalEmail(req.getPersonalEmail());
        user.setBatch(req.getBatch());
        user.setSource(masterDataService.normalizeAndValidate(MasterDataCategory.CANDIDATE_SOURCE, req.getSource()));
        user.setSkillSet(masterDataService.normalizeAndValidate(MasterDataCategory.SKILL_SET, req.getSkillSet()));
        user.setYoeActual(req.getYoeActual());
        user.setYoePortrayed(req.getYoePortrayed());
        user.setYop(req.getYop());
        user.setBranch(BranchAccess.defaultBranch());
        userRepository.save(user);
        
        try {
            emailService.sendCandidateRegistrationWelcomeEmail(user.getEmail(), user.getName());
        } catch (Exception e) {
            logger.warn("Failed to send registration welcome email to {}: {}",
                PiiRedactor.maskEmail(user.getEmail()), e.getMessage());
        }
        
        auditService.record(user.getId(), user.getName(), "CANDIDATE", "CANDIDATE_REGISTERED",
                "CANDIDATE", user.getId(),
                String.format("Registered: %s (%s) - %s", user.getName(),
                        PiiRedactor.maskEmail(user.getEmail()),
                        user.getSource() != null ? user.getSource() : "N/A"));
        
        return ResponseEntity.ok(new ApiMessageResponse(true, "Registration successful. You can now log in."));
    }

    /** POST /auth/candidates — ADMIN creates a single candidate account */
    @PostMapping("/candidates")
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
    public ResponseEntity<?> createCandidate(@Valid @RequestBody CreateCandidateRequest req,
                                           @RequestHeader("X-User-Id") String callerId,
                                           @RequestHeader("X-User-Role") String callerRole) {
        String normalizedSource = masterDataService.normalizeAndValidate(
                MasterDataCategory.CANDIDATE_SOURCE, req.getSource());
        List<String> allowedSources = getAllowedSources(callerId, callerRole);
        if (allowedSources != null && !allowedSources.contains(normalizedSource)) {
            return ResponseEntity.status(403).body(Map.of(
                    "ok", false,
                    "error", "You cannot create candidates outside your allowed source"));
        }
        String branch = BranchAccess.resolveBranchForCreate(callerRole, req.getBranch());

        try {
            BulkImportService.CreatedCandidate created = bulkImportService.createSingleCandidate(req, branch);
            auditService.record(callerId, null, callerRole, "CANDIDATE_CREATED", "CANDIDATE", created.getId(),
                    String.format("Created candidate %s (%s)", created.getName(),
                            PiiRedactor.maskEmail(created.getUsername())));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", true);
            response.put("message", "Candidate created successfully. Welcome email sent if an address is configured.");
            response.put("candidateId", created.getId());
            response.put("username", created.getUsername());
            response.put("password", created.getPassword());
            response.put("candidate", buildCandidateMap(userRepository.findById(created.getId()).orElseThrow()));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to create candidate", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Failed to create candidate"));
        }
    }

    /** PATCH /auth/candidates/{id} — ADMIN updates rating, status, no_of_interviews */
    @PatchMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
    public ResponseEntity<?> updateCandidate(@PathVariable String id,
                                              @RequestBody UpdateCandidateRequest req,
                                              @RequestHeader("X-User-Id") String callerId,
                                              @RequestHeader("X-User-Role") String callerRole) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (user.getRole() != UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "User is not a candidate"));
        }
        if (!canStaffAccessCandidate(callerId, callerRole, user)) {
            return ResponseEntity.notFound().build();
        }
        if (req.getRating() != null) {
            user.setRating(masterDataService.normalizeAndValidate(MasterDataCategory.CANDIDATE_RATING, req.getRating()));
        }
        if (req.getCandidateStatus() != null) {
            user.setCandidateStatus(masterDataService.normalizeAndValidate(MasterDataCategory.CANDIDATE_STATUS, req.getCandidateStatus()));
        }
        if (req.getNoOfInterviews() != null) user.setNoOfInterviews(req.getNoOfInterviews());
        // Extended fields — all roles
        if (req.getName() != null) user.setName(req.getName());
        if (req.getContactNumber() != null) user.setContactNumber(req.getContactNumber());
        if (req.getOfficialEmail() != null) user.setOfficialEmail(req.getOfficialEmail());
        if (req.getPersonalEmail() != null) user.setPersonalEmail(req.getPersonalEmail());
        if (req.getBatch() != null) user.setBatch(req.getBatch());
        if (req.getBatchMentor() != null) user.setBatchMentor(req.getBatchMentor());
        if (req.getSkillSet() != null) {
            user.setSkillSet(masterDataService.normalizeAndValidate(MasterDataCategory.SKILL_SET, req.getSkillSet()));
        }
        if (req.getYoeActual() != null) user.setYoeActual(req.getYoeActual());
        if (req.getYoePortrayed() != null) user.setYoePortrayed(req.getYoePortrayed());
        if (req.getYop() != null) user.setYop(req.getYop());
        if (req.getInterviewMentorName() != null) user.setInterviewMentorName(req.getInterviewMentorName());
        if (req.getClientName() != null) user.setClientName(req.getClientName());
        // Soft delete/restore — inactive candidates are blocked at login but the row is kept.
        if (req.getActive() != null) user.setActive(req.getActive());
        // Source and email — SUPER_ADMIN only
        if (callerRole.equals("SUPER_ADMIN")) {
            if (req.getSource() != null) {
                user.setSource(masterDataService.normalizeAndValidate(MasterDataCategory.CANDIDATE_SOURCE, req.getSource()));
            }
            if (req.getEmail() != null) user.setEmail(req.getEmail());
            if (req.getBranch() != null && Branch.isValid(req.getBranch())) {
                user.setBranch(Branch.normalize(req.getBranch()));
            }
        }
        userRepository.save(user);

        // Log audit trail
        StringBuilder changes = new StringBuilder();
        if (req.getRating() != null) changes.append("Rating: ").append(req.getRating()).append(", ");
        if (req.getCandidateStatus() != null) changes.append("Status: ").append(req.getCandidateStatus()).append(", ");
        if (req.getNoOfInterviews() != null) changes.append("Interviews: ").append(req.getNoOfInterviews()).append(", ");
        if (req.getName() != null) changes.append("Name: ").append(req.getName()).append(", ");
        if (req.getSource() != null) changes.append("Source: ").append(req.getSource());
        if (req.getActive() != null) changes.append("Active: ").append(req.getActive());

        String auditAction = req.getActive() != null
                ? (req.getActive() ? "CANDIDATE_REACTIVATED" : "CANDIDATE_DEACTIVATED")
                : "CANDIDATE_UPDATED";
        auditService.record(callerId, null, callerRole, auditAction, "CANDIDATE", user.getId(),
            String.format("Updated %s: %s", user.getName(), changes.toString()));
        
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
        // ADMIN / TESTING_ADMIN require adminSource
        if ((req.getRole() == UserRole.ADMIN || req.getRole() == UserRole.TESTING_ADMIN)
                && req.getAdminSource() == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "adminSource is required for ADMIN and TESTING_ADMIN roles"));
        }
        if (req.getRole() == UserRole.ADMIN || req.getRole() == UserRole.TESTING_ADMIN) {
            masterDataService.requireValid(MasterDataCategory.ADMIN_SOURCE, req.getAdminSource());
        }
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "error", "Email already registered"));
        }
        // Explicit branch wins (validated against master data); otherwise default from role as before.
        String resolvedBranch;
        if (req.getBranch() != null && !req.getBranch().isBlank()) {
            masterDataService.requireValid(MasterDataCategory.BRANCH, req.getBranch());
            resolvedBranch = masterDataService.normalizeCode(req.getBranch());
        } else {
            resolvedBranch = BranchAccess.resolveStaffBranch(req.getRole());
        }
        User user = new User();
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setPassword(passwordService.encode(req.getPassword()));
        user.setRole(req.getRole());
        user.setBranch(resolvedBranch);
        if (req.getRole() == UserRole.ADMIN || req.getRole() == UserRole.TESTING_ADMIN) {
            user.setAdminSource(masterDataService.normalizeCode(req.getAdminSource()));
        }
        userRepository.save(user);
        
        // Log audit trail
        auditService.record("system", "System", "SUPER_ADMIN", "STAFF_CREATED", "STAFF", user.getId(),
            String.format("Created %s account: %s (%s)", req.getRole(), user.getName(),
                    PiiRedactor.maskEmail(user.getEmail())));
        
        return ResponseEntity.ok(Map.of("ok", true, "message", "Staff account created successfully"));
    }

    /** PATCH /auth/staff/{id} — update a staff account (SUPER_ADMIN only) */
    @PatchMapping("/staff/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> updateStaff(
            @PathVariable String id,
            @Valid @RequestBody UpdateStaffRequest req,
            @RequestHeader("X-User-Role") String callerRole,
            @RequestHeader("X-User-Id") String callerId) {

        User user = userRepository.findById(id).orElse(null);
        if (user == null || !STAFF_ROLES.contains(user.getRole())) {
            return ResponseEntity.notFound().build();
        }

        String email = req.getEmail().trim().toLowerCase();
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "error", "Email already registered"));
        }

        boolean isSuperAdminAccount = user.getRole() == UserRole.SUPER_ADMIN;
        boolean editingSelf = id.equals(callerId);

        if (!isSuperAdminAccount) {
            if (!STAFF_ROLES.contains(req.getRole()) || req.getRole() == UserRole.SUPER_ADMIN) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error",
                    "Invalid role. Must be RECRUITER, TESTING_RECRUITER, ADMIN, or TESTING_ADMIN"));
            }
            if (editingSelf && req.getRole() != user.getRole()) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "You cannot change your own role"));
            }
            if (req.getRole() == UserRole.ADMIN || req.getRole() == UserRole.TESTING_ADMIN) {
                if (req.getAdminSource() == null || req.getAdminSource().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("ok", false, "error",
                        "adminSource is required for ADMIN and TESTING_ADMIN roles"));
                }
                masterDataService.requireValid(MasterDataCategory.ADMIN_SOURCE, req.getAdminSource());
                user.setAdminSource(masterDataService.normalizeCode(req.getAdminSource()));
            } else {
                user.setAdminSource(null);
            }
            // Explicit branch wins (validated against master data). Otherwise: re-derive only when
            // the role actually changed (matches legacy promote/demote behavior); if role is
            // unchanged, leave the currently assigned branch untouched — resetting it on every
            // unrelated edit (name/password) would defeat manual per-user branch assignment.
            if (req.getBranch() != null && !req.getBranch().isBlank()) {
                masterDataService.requireValid(MasterDataCategory.BRANCH, req.getBranch());
                user.setBranch(masterDataService.normalizeCode(req.getBranch()));
            } else if (req.getRole() != user.getRole()) {
                user.setBranch(BranchAccess.resolveStaffBranch(req.getRole()));
            }
            user.setRole(req.getRole());
        } else if (editingSelf && req.getRole() != UserRole.SUPER_ADMIN) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "You cannot change your own role"));
        }

        user.setName(req.getName().trim());
        user.setEmail(email);
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            if (req.getPassword().length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Password must be at least 6 characters"));
            }
            user.setPassword(passwordService.encode(req.getPassword()));
        }
        userRepository.save(user);

        auditService.record(callerId, null, callerRole, "STAFF_UPDATED", "STAFF", user.getId(),
            String.format("Updated %s account: %s (%s)", user.getRole(), user.getName(),
                    PiiRedactor.maskEmail(user.getEmail())));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("id", user.getId());
        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put("role", user.getRole().name());
        if (user.getAdminSource() != null) {
            response.put("adminSource", user.getAdminSource());
        }
        response.put("branch", user.getBranch());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Login", description = "Unified login for candidates and staff")
    @ApiResponse(responseCode = "200", description = "Authenticated",
            content = @Content(schema = @Schema(implementation = AuthTokenResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        String email = req.getUsername() != null ? req.getUsername().trim() : null;
        if (loginAttemptService.isLocked(email)) {
            return ResponseEntity.status(429).body(
                    new ApiErrorResponse("Too many failed login attempts. Please try again later."));
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            loginAttemptService.recordFailure(email);
            auditService.recordLoginFailure(email);
            return ResponseEntity.status(401).body(new ApiErrorResponse("Invalid credentials"));
        }
        if (!passwordService.matches(req.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(email);
            auditService.recordLoginFailure(email);
            return ResponseEntity.status(401).body(new ApiErrorResponse("Invalid credentials"));
        }
        if (user.getRole() == UserRole.CANDIDATE && !user.isActive()) {
            return ResponseEntity.status(401).body(
                    new ApiErrorResponse("Your account is not yet active. Please wait until your interview is scheduled."));
        }
        loginAttemptService.recordSuccess(email);

        if (passwordService.needsUpgrade(user.getPassword())) {
            user.setPassword(passwordService.encode(req.getPassword()));
            userRepository.save(user);
        }
        if (req.getRole() != null && req.getRole() != user.getRole()) {
            return ResponseEntity.status(403).body(new ApiErrorResponse(
                "Your account role is " + user.getRole().name() + ", not " + req.getRole().name()));
        }

        if (user.getRole().isStaff()) {
            String expectedBranch = BranchAccess.resolveStaffBranch(user.getRole());
            if (!expectedBranch.equals(user.getBranch())) {
                user.setBranch(expectedBranch);
                userRepository.save(user);
            }
        }

        JwtService.TokenPair tokenPair = jwtService.generateTokenPair(user);
        refreshTokenService.storeRefreshToken(user, tokenPair.refreshJti());
        auditService.recordLoginSuccess(user.getId(), user.getRole().name(), user.getEmail());

        return ResponseEntity.ok(AuthTokenResponse.from(user, tokenPair));
    }

    @Operation(summary = "Refresh tokens", description = "Exchange a refresh token for a new access/refresh pair")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AuthTokenResponse.class)))
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        var refreshed = refreshTokenService.refresh(req.getRefreshToken());
        if (refreshed.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(new ApiErrorResponse("Invalid or expired refresh token"));
        }
        JwtService.TokenPair pair = refreshed.get();
        jwtService.parseAccessToken(pair.accessToken()).ifPresent(tokenUser ->
                auditService.recordTokenRefresh(tokenUser.userId(), tokenUser.role()));
        return ResponseEntity.ok(AuthTokenResponse.from(pair));
    }

    @Operation(summary = "Logout", description = "Revoke access and refresh tokens")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiSuccessResponse.class)))
    @PostMapping("/logout")
    public ResponseEntity<ApiSuccessResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) LogoutRequest req) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            jwtService.parseAccessToken(token).ifPresent(tokenUser ->
                    auditService.recordLogout(tokenUser.userId(), tokenUser.role()));
            jwtService.revokeToken(token);
        }
        if (req != null && req.getRefreshToken() != null && !req.getRefreshToken().isBlank()) {
            refreshTokenService.revoke(req.getRefreshToken());
        }
        return ResponseEntity.ok(new ApiSuccessResponse(true));
    }

    /** POST /auth/internal/introspect — token active check for API gateway */
    @PostMapping("/internal/introspect")
    public ResponseEntity<?> introspect(@Valid @RequestBody TokenIntrospectRequest req) {
        boolean active = jwtService.isTokenActive(req.getToken());
        return ResponseEntity.ok(Map.of("active", active));
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
            logger.info("OTP sent successfully to: {}", PiiRedactor.maskEmail(req.getEmail()));
        } catch (Exception e) {
            logger.error("Failed to send OTP email to {}: {}", PiiRedactor.maskEmail(req.getEmail()), e.getMessage());
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
        User user = userRepository.findByEmailIgnoreCase(req.getEmail()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "User not found"));
        }
        user.setPassword(passwordService.encode(req.getNewPassword()));
        userRepository.save(user);
        otpService.markOtpAsUsed(req.getEmail(), req.getOtp());
        
        auditService.record(user.getId(), user.getName(), user.getRole().name(), "PASSWORD_RESET",
                "AUTH", user.getId(), "Password reset via OTP");
        
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

    /** GET /auth/users/by-email/{email} — internal service-to-service candidate lookup */
    @GetMapping("/users/by-email/{email}")
    public ResponseEntity<?> getUserByEmail(@PathVariable String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        if (user.getRole() == UserRole.CANDIDATE) {
            return ResponseEntity.ok(buildCandidateMap(user));
        }
        return ResponseEntity.ok(buildUserMap(user));
    }

    /** GET /auth/candidates — search registered candidates (filtered by branch and admin source) */
    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('" + STAFF_READ_ROLES + "')")
    public ResponseEntity<?> getCandidates(@RequestParam(required = false, defaultValue = "") String search,
                                            @RequestHeader("X-User-Id") String callerId,
                                            @RequestHeader("X-User-Role") String callerRole) {
        List<User> candidates = findCandidatesForCaller(search, callerId, callerRole);
        return ResponseEntity.ok(candidates.stream()
            .map(this::buildCandidateMap)
            .toList());
    }

    /** GET /auth/candidates/{id} — get full candidate profile */
    @GetMapping("/candidates/{id}")
    public ResponseEntity<?> getCandidateById(@PathVariable String id,
                                            @RequestHeader(value = "X-User-Id", required = false) String callerId,
                                            @RequestHeader(value = "X-User-Role", required = false) String callerRole) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (user.getRole() != UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not a candidate"));
        }
        if (callerId != null && callerId.equals(id)) {
            return ResponseEntity.ok(buildCandidateMap(user));
        }
        if ("CANDIDATE".equals(callerRole) && !id.equals(callerId)) {
            return ResponseEntity.notFound().build();
        }
        if (callerRole != null && isStaffReadRole(callerRole)
                && !canStaffAccessCandidate(callerId, callerRole, user)) {
            return ResponseEntity.notFound().build();
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
                    map.put("adminSource", u.getAdminSource());
                }
                if (u.getRole().isStaff()) {
                    map.put("branch", u.getBranch() != null && !u.getBranch().isBlank()
                            ? u.getBranch() : BranchAccess.resolveStaffBranch(u.getRole()));
                }
                return map;
            })
            .toList());
    }

    /** GET /auth/admins — internal endpoint for digest service */
    @GetMapping("/admins")
    public ResponseEntity<?> listAdmins() {
        List<User> admins = userRepository.findByRoleIn(
                List.of(UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.TESTING_ADMIN));
        return ResponseEntity.ok(admins.stream()
            .map(u -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", u.getId());
                map.put("email", u.getEmail());
                map.put("name", u.getName() != null ? u.getName() : "");
                map.put("role", u.getRole().name());
                map.put("branch", u.getBranch() != null ? u.getBranch()
                        : BranchAccess.resolveStaffBranch(u.getRole()));
                return map;
            })
            .toList());
    }

    /** GET /auth/internal/candidates — service-to-service: all active candidates for email dispatch */
    @GetMapping("/internal/candidates")
    public ResponseEntity<?> listCandidatesInternal() {
        List<User> candidates = userRepository.findByRole(UserRole.CANDIDATE);
        return ResponseEntity.ok(candidates.stream()
            .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
            .map(u -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", u.getId());
                map.put("name", u.getName() != null ? u.getName() : "");
                map.put("email", u.getEmail());
                map.put("phone", u.getContactNumber());
                return map;
            })
            .toList());
    }

    /** GET /auth/internal/pipeline-status — branch-scoped pipeline for digest */
    @GetMapping("/internal/pipeline-status")
    public ResponseEntity<?> getInternalPipelineStatus(@RequestParam(required = false) String branch) {
        List<User> candidates;
        if (branch == null || branch.isBlank()) {
            candidates = userRepository.findByRole(UserRole.CANDIDATE);
        } else {
            candidates = userRepository.findByRoleAndBranch(UserRole.CANDIDATE, Branch.normalize(branch));
        }
        return ResponseEntity.ok(buildPipelineStatusMap(candidates));
    }

    /** GET /auth/internal/client-notification-recipients — service-to-service for client alerts */
    @GetMapping("/internal/client-notification-recipients")
    public ResponseEntity<?> listClientNotificationRecipients() {
        List<User> recipients = userRepository.findByRoleIn(
            List.of(UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.TESTING_ADMIN,
                    UserRole.RECRUITER, UserRole.TESTING_RECRUITER));
        return ResponseEntity.ok(recipients.stream()
            .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
            .map(u -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("email", u.getEmail());
                map.put("name", u.getName() != null ? u.getName() : "");
                map.put("role", u.getRole().name());
                if (u.getAdminSource() != null) {
                    map.put("adminSource", u.getAdminSource());
                }
                if (u.getRole().isStaff()) {
                    map.put("branch", u.getBranch() != null ? u.getBranch()
                            : BranchAccess.resolveStaffBranch(u.getRole()));
                }
                return map;
            })
            .toList());
    }

    /** GET /auth/candidates/pipeline-status — candidate pipeline analytics for daily report */
    @GetMapping("/candidates/pipeline-status")
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
    public ResponseEntity<?> getCandidatePipelineStatus(
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestHeader(value = "X-User-Role", required = false) String callerRole) {
        List<User> candidates = findCandidatesForCaller("",
            callerId, callerRole != null ? callerRole : "SUPER_ADMIN");
        return ResponseEntity.ok(buildPipelineStatusMap(candidates));
    }

    private Map<String, Object> buildPipelineStatusMap(List<User> candidates) {
        Map<String, Object> pipeline = new HashMap<>();
        
        // Count by status
        long rfdCount = candidates.stream().filter(c -> "RFD".equals(c.getCandidateStatus())).count();
        long wfdCount = candidates.stream().filter(c -> "WFD".equals(c.getCandidateStatus())).count();
        long dobCount = candidates.stream().filter(c -> "DOB".equals(c.getCandidateStatus())).count();
        long deployedCount = candidates.stream().filter(c -> "DEPLOYED".equals(c.getCandidateStatus())).count();
        
        pipeline.put("rfd", rfdCount);
        pipeline.put("wfd", wfdCount);
        pipeline.put("dob", dobCount);
        pipeline.put("deployed", deployedCount);
        
        // Count by source
        Map<String, Long> bySource = candidates.stream()
            .filter(c -> c.getSource() != null)
            .collect(Collectors.groupingBy(User::getSource, Collectors.counting()));
        pipeline.put("bySource", bySource);
        
        // Count by rating
        Map<String, Long> byRating = candidates.stream()
            .filter(c -> c.getRating() != null)
            .collect(Collectors.groupingBy(User::getRating, Collectors.counting()));
        pipeline.put("byRating", byRating);
        
        // Count by skill set
        Map<String, Long> bySkillSet = candidates.stream()
            .filter(c -> c.getSkillSet() != null)
            .collect(Collectors.groupingBy(User::getSkillSet, Collectors.counting()));
        pipeline.put("bySkillSet", bySkillSet);
        
        // Today's registrations
        Instant startOfToday = java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        long todayRegistrations = candidates.stream()
            .filter(c -> c.getCreatedAt() != null)
            .filter(c -> c.getCreatedAt().isAfter(startOfToday))
            .count();
        pipeline.put("todayRegistrations", todayRegistrations);
        
        pipeline.put("totalCandidates", candidates.size());
        
        return pipeline;
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
        auditService.record(callerId, null, callerRole, "STAFF_DELETED", "STAFF", target.getId(),
            String.format("Deleted %s account: %s (%s)", target.getRole(), target.getName(),
                    PiiRedactor.maskEmail(target.getEmail())));
        
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
            map.put("adminSource", user.getAdminSource());
        }
        if (user != null && user.getRole().isStaff()) {
            map.put("branch", user.getBranch() != null && !user.getBranch().isBlank()
                    ? user.getBranch()
                    : BranchAccess.resolveStaffBranch(user.getRole()));
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
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
    public ResponseEntity<?> bulkImportFromApi(@RequestParam String gdriveFileUrl,
                                               @RequestHeader("X-User-Id") String userId,
                                               @RequestHeader("X-User-Role") String callerRole) {
        
        if (gdriveFileUrl == null || gdriveFileUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Google Drive file URL is required"));
        }
        
        try {
            BulkImportResponse result = candidateBulkImportService.importFromThirdPartyApi(gdriveFileUrl, userId, callerRole);
            
            // Log audit trail
            auditService.record(userId, null, callerRole, "BULK_IMPORT_API", "CANDIDATES", "bulk-import",
                String.format("API bulk import - Success: %d, Skipped: %d, Errors: %d",
                    result.getSuccessCount(), result.getSkippedCount(), result.getErrorCount()),
                null, gdriveFileUrl);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("API bulk import failed for user: {}", userId, e);
            
            // Log audit trail for failure
            auditService.record(userId, null, callerRole, "BULK_IMPORT_API_FAILED", "CANDIDATES", "bulk-import",
                "API bulk import failed: " + e.getMessage(), null, gdriveFileUrl);
            
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Import failed: " + e.getMessage()));
        }
    }

    /** POST /auth/candidates/bulk-upload — Upload Excel file for bulk import validation */
    @PostMapping("/candidates/bulk-upload")
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
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
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
    public ResponseEntity<?> confirmBulkImport(@PathVariable String sessionId,
                                              @RequestHeader("X-User-Role") String callerRole,
                                              @RequestBody(required = false) Map<String, String> body) {

        try {
            String requestedBranch = body != null ? body.get("branch") : null;
            String branch = BranchAccess.resolveBranchForCreate(callerRole, requestedBranch);
            BulkImportService.BulkImportResult result = bulkImportService.processBulkImport(sessionId, branch);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", true);
            response.put("message", result.getErrorCount() == 0
                ? "Bulk import completed — " + result.getSuccessCount() + " candidates created"
                : "Bulk import failed — no candidates were created");
            response.put("successCount", result.getSuccessCount());
            response.put("errorCount", result.getErrorCount());
            response.put("errors", result.getErrors());
            response.put("sessionId", sessionId);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", e.getMessage(),
                "successCount", 0,
                "errorCount", 0,
                "errors", List.of()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Import failed: " + e.getMessage()));
        }
    }

    /** GET /auth/candidates/bulk-download/{sessionId} — Download credentials Excel after successful import */
    @GetMapping("/candidates/bulk-download/{sessionId}")
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
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
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
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
                
                user.setPassword(passwordService.encode(newPassword));
                userRepository.save(user);
                
                credentialList.add(new BulkImportService.CreatedCandidate(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    newPassword, // Show the new readable password
                    user.getSource() != null ? user.getSource() : "UNKNOWN",
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
    private List<String> getAllowedSources(String userId, String role) {
        if (!"ADMIN".equals(role) && !"TESTING_ADMIN".equals(role)) return null;
        User admin = userRepository.findById(userId).orElse(null);
        if (admin == null || admin.getAdminSource() == null) return null;
        return masterDataService.getAllowedCandidateSources(admin.getAdminSource());
    }

    private List<User> findCandidatesForCaller(String search, String callerId, String callerRole) {
        String allowedBranch = BranchAccess.resolveAllowedBranch(callerRole);
        List<String> allowedSources = callerId != null ? getAllowedSources(callerId, callerRole) : null;
        boolean includeNullSource = shouldIncludeNullCandidateSource(allowedSources);
        boolean blankSearch = search == null || search.isBlank();

        if (allowedBranch == null && allowedSources == null) {
            return blankSearch
                ? userRepository.findByRole(UserRole.CANDIDATE)
                : userRepository.searchCandidates(search);
        }
        if (allowedSources == null) {
            return blankSearch
                ? userRepository.findByRoleAndBranch(UserRole.CANDIDATE, allowedBranch)
                : userRepository.searchCandidatesByBranch(search, allowedBranch);
        }
        if (allowedBranch == null) {
            return blankSearch
                ? userRepository.findCandidatesBySources(UserRole.CANDIDATE, allowedSources, includeNullSource)
                : userRepository.searchCandidatesBySources(search, allowedSources, includeNullSource);
        }
        return blankSearch
            ? userRepository.findCandidatesByBranchAndSources(
                UserRole.CANDIDATE, allowedBranch, allowedSources, includeNullSource)
            : userRepository.searchCandidatesByBranchAndSources(
                search, allowedBranch, allowedSources, includeNullSource);
    }

    /** Bench/B2B admins also see candidates imported without an explicit source (common for TRAINING). */
    private boolean shouldIncludeNullCandidateSource(List<String> allowedSources) {
        if (allowedSources == null || allowedSources.isEmpty()) {
            return false;
        }
        return allowedSources.contains("BENCH") || allowedSources.contains("B2B");
    }

    private boolean canAdminAccessCandidate(String adminId, User candidate) {
        User admin = userRepository.findById(adminId).orElse(null);
        if (admin == null || admin.getAdminSource() == null) return true;
        List<String> allowed = masterDataService.getAllowedCandidateSources(admin.getAdminSource());
        return candidate.getSource() == null || allowed.contains(candidate.getSource());
    }

    private boolean isStaffReadRole(String role) {
        return "SUPER_ADMIN".equals(role)
            || "ADMIN".equals(role)
            || "TESTING_ADMIN".equals(role)
            || "RECRUITER".equals(role)
            || "TESTING_RECRUITER".equals(role);
    }

    private boolean canStaffAccessCandidate(String callerId, String callerRole, User candidate) {
        if (candidate == null || candidate.getRole() != UserRole.CANDIDATE) {
            return false;
        }
        if (!BranchAccess.canAccessBranch(callerRole, candidate.getBranch())) {
            return false;
        }
        if ("ADMIN".equals(callerRole) || "TESTING_ADMIN".equals(callerRole)) {
            return canAdminAccessCandidate(callerId, candidate);
        }
        return isStaffReadRole(callerRole);
    }

    private User requireAccessibleCandidate(String id, String callerId, String callerRole) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null || user.getRole() != UserRole.CANDIDATE) {
            return null;
        }
        if (callerId != null && callerId.equals(id)) {
            return user;
        }
        if (callerRole != null && isStaffReadRole(callerRole)
                && !canStaffAccessCandidate(callerId, callerRole, user)) {
            return null;
        }
        if ("CANDIDATE".equals(callerRole) && !id.equals(callerId)) {
            return null;
        }
        return user;
    }

    private Map<String, Object> buildUserMap(User u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", u.getId());
        map.put("name", u.getName() != null ? u.getName() : "");
        map.put("email", u.getEmail());
        map.put("role", u.getRole().name());
        if (u.getAdminSource() != null) {
            map.put("adminSource", u.getAdminSource());
        }
        if (u.getRole().isStaff()) {
            map.put("branch", u.getBranch() != null ? u.getBranch()
                    : BranchAccess.resolveStaffBranch(u.getRole()));
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
        map.put("source", u.getSource());
        map.put("branch", u.getBranch() != null ? u.getBranch() : BranchAccess.defaultBranch());
        map.put("candidateStatus", u.getCandidateStatus());
        map.put("rating", u.getRating());
        map.put("skillSet", u.getSkillSet());
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
        map.put("active", u.isActive());

        return map;
    }

    /** POST /auth/candidates/deployment/bulk-import — Bulk import deployment data */
    @PostMapping("/candidates/deployment/bulk-import")
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
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
    @PreAuthorize("hasAnyRole('" + STAFF_READ_ROLES + "')")
    public ResponseEntity<?> getDeployedCandidates(@RequestHeader("X-User-Role") String callerRole) {
        List<User> deployed = deploymentService.getDeployedCandidates();
        String allowedBranch = BranchAccess.resolveAllowedBranch(callerRole);
        if (allowedBranch != null) {
            deployed = deployed.stream()
                .filter(u -> allowedBranch.equals(Branch.normalize(u.getBranch())))
                .toList();
        }
        return ResponseEntity.ok(deployed.stream()
            .map(this::buildCandidateMap)
            .toList());
    }

    /** PATCH /auth/candidates/{id}/deployment — Update deployment fields */
    @PatchMapping("/candidates/{id}/deployment")
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
    public ResponseEntity<?> updateDeployment(@PathVariable String id,
                                              @RequestBody Map<String, Object> deploymentData,
                                              @RequestHeader("X-User-Id") String callerId,
                                              @RequestHeader("X-User-Role") String callerRole) {
        User candidate = requireAccessibleCandidate(id, callerId, callerRole);
        if (candidate == null) {
            return ResponseEntity.notFound().build();
        }

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
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
    public ResponseEntity<?> clearDeployment(@PathVariable String id,
                                             @RequestHeader("X-User-Id") String callerId,
                                             @RequestHeader("X-User-Role") String callerRole) {
        User candidate = requireAccessibleCandidate(id, callerId, callerRole);
        if (candidate == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            User updated = deploymentService.clearDeployment(id);
            return ResponseEntity.ok(buildCandidateMap(updated));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /** GET /auth/candidates/{id}/deployment-history — Get deployment history for a candidate */
    @GetMapping("/candidates/{id}/deployment-history")
    @PreAuthorize("hasAnyRole('" + STAFF_READ_ROLES + "')")
    public ResponseEntity<?> getDeploymentHistory(@PathVariable String id,
                                                  @RequestHeader("X-User-Id") String callerId,
                                                  @RequestHeader("X-User-Role") String callerRole) {
        User candidate = requireAccessibleCandidate(id, callerId, callerRole);
        if (candidate == null) {
            return ResponseEntity.notFound().build();
        }

        logger.info("Fetching deployment history for candidate ID: {}", id);
        List<DeploymentHistory> history = deploymentService.getDeploymentHistory(id);
        logger.info("Found {} deployment history records for candidate ID: {}", history.size(), id);
        
        return ResponseEntity.ok(history.stream()
            .map(this::buildDeploymentHistoryMap)
            .toList());
    }

    /** POST /auth/candidates/{id}/end-deployment — End current deployment and move back to B2B */
    @PostMapping("/candidates/{id}/end-deployment")
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
    public ResponseEntity<?> endDeployment(@PathVariable String id,
                                          @RequestBody(required = false) Map<String, Object> requestBody,
                                          @RequestHeader("X-User-Id") String callerId,
                                          @RequestHeader("X-User-Role") String callerRole) {
        User candidate = requireAccessibleCandidate(id, callerId, callerRole);
        if (candidate == null) {
            return ResponseEntity.notFound().build();
        }

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
    @PreAuthorize("hasAnyRole('" + STAFF_READ_ROLES + "')")
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

    /** POST /auth/candidates/market — Create a market candidate (name, email, phone only). Starts inactive. */
    @PostMapping("/candidates/market")
    @PreAuthorize("hasAnyRole('" + STAFF_ADMIN_ROLES + "')")
    public ResponseEntity<?> createMarketCandidate(@RequestBody Map<String, String> body,
                                                   @RequestHeader("X-User-Branch") String callerBranch) {
        String name = body.get("name");
        String email = body.get("email");
        String contactNumber = body.get("contactNumber");

        if (name == null || name.isBlank() || email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse("name and email are required"));
        }
        if (userRepository.findByEmailIgnoreCase(email.trim()).isPresent()) {
            return ResponseEntity.status(409).body(new ApiErrorResponse("A user with this email already exists"));
        }

        String rawPassword = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setName(name.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setContactNumber(contactNumber != null ? contactNumber.trim() : null);
        user.setPassword(passwordService.encode(rawPassword));
        user.setRole(UserRole.CANDIDATE);
        user.setSource("MARKET");
        user.setActive(false);
        user.setBranch(Branch.normalize(callerBranch));
        userRepository.save(user);

        try {
            emailService.sendCandidateWelcomeEmail(user.getEmail(), user.getName(), user.getEmail(), rawPassword);
        } catch (Exception e) {
            logger.warn("Failed to send welcome email to market candidate {}: {}", user.getEmail(), e.getMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put("contactNumber", user.getContactNumber());
        response.put("source", user.getSource());
        response.put("active", user.isActive());
        response.put("generatedPassword", rawPassword);
        return ResponseEntity.status(201).body(response);
    }

    /** POST /auth/internal/notify/interview-scheduled — Send interview scheduled email (called by interview-service). */
    @PostMapping("/internal/notify/interview-scheduled")
    public ResponseEntity<?> notifyInterviewScheduled(@RequestBody Map<String, String> body) {
        String toEmail    = body.get("email");
        String name       = body.get("name");
        String scheduledAt = body.get("scheduledAt");
        String expiresAt  = body.get("expiresAt");

        if (toEmail == null || toEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        try {
            emailService.sendInterviewScheduledEmail(toEmail, name, scheduledAt, expiresAt);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            logger.warn("Failed to send interview scheduled email to {}: {}", toEmail, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /** PATCH /auth/internal/users/{userId}/active — Activate or deactivate a user (internal service-to-service). */
    @PatchMapping("/internal/users/{userId}/active")
    public ResponseEntity<?> setUserActive(@PathVariable String userId,
                                           @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse("'active' field is required"));
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(new ApiErrorResponse("User not found"));
        }
        user.setActive(active);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("id", userId, "active", active));
    }

}
