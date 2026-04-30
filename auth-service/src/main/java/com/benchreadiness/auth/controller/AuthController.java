package com.benchreadiness.auth.controller;

import com.benchreadiness.auth.dto.CreateStaffRequest;
import com.benchreadiness.auth.dto.LoginRequest;
import com.benchreadiness.auth.dto.RegisterRequest;
import com.benchreadiness.auth.dto.UpdateCandidateRequest;
import com.benchreadiness.auth.entity.*;
import com.benchreadiness.auth.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Set<UserRole> STAFF_ROLES =
        Set.of(UserRole.RECRUITER, UserRole.ADMIN, UserRole.SUPER_ADMIN);

    private static final List<CandidateSource> BENCH_SOURCES = List.of(CandidateSource.BENCH);
    private static final List<CandidateSource> BD_SOURCES = List.of(CandidateSource.B2B);
    private static final List<CandidateSource> RECRUITMENT_SOURCES = List.of(CandidateSource.MARKET);

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
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
        return ResponseEntity.ok(Map.of("ok", true, "message", "Registration successful. You can now log in."));
    }

    /** PATCH /auth/candidates/{id} — ADMIN updates rating, status, no_of_interviews */
    @PatchMapping("/candidates/{id}")
    public ResponseEntity<?> updateCandidate(@PathVariable String id,
                                              @RequestBody UpdateCandidateRequest req,
                                              @RequestHeader("X-User-Id") String callerId,
                                              @RequestHeader("X-User-Role") String callerRole) {
        if (!callerRole.equals("ADMIN") && !callerRole.equals("SUPER_ADMIN")) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "Only ADMIN can update candidates"));
        }
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
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("ok", true, "message", "Candidate updated"));
    }

    /** POST /auth/staff — SUPER_ADMIN creates a staff account */
    @PostMapping("/staff")
    public ResponseEntity<?> createStaff(@Valid @RequestBody CreateStaffRequest req,
                                          @RequestHeader("X-User-Role") String callerRole) {
        if (!callerRole.equals("SUPER_ADMIN")) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "Only SUPER_ADMIN can create staff accounts"));
        }
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

    /** GET /auth/users/{id} — internal service-to-service lookup */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        return userRepository.findById(id)
            .map(u -> ResponseEntity.ok(buildUserMap(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    /** GET /auth/candidates — search registered candidates (filtered by admin source) */
    @GetMapping("/candidates")
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
    public ResponseEntity<?> listStaff(@RequestHeader("X-User-Role") String callerRole) {
        if (!callerRole.equals("SUPER_ADMIN")) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
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

    /** DELETE /auth/staff/{id} — SUPER_ADMIN removes a staff account */
    @DeleteMapping("/staff/{id}")
    public ResponseEntity<?> deleteStaff(@PathVariable String id,
                                          @RequestHeader("X-User-Role") String callerRole,
                                          @RequestHeader("X-User-Id") String callerId) {
        if (!callerRole.equals("SUPER_ADMIN")) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        if (id.equals(callerId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete your own account"));
        }
        User target = userRepository.findById(id).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();
        if (target.getRole() == UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete candidate accounts via this endpoint"));
        }
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
    public ResponseEntity<?> updateMyProfile(@RequestHeader("X-User-Id") String userId,
                                              @RequestHeader("X-User-Role") String callerRole,
                                              @RequestBody Map<String, Object> updates) {
        if (!callerRole.equals("CANDIDATE")) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "Only candidates can update their profile"));
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if (updates.containsKey("name")) user.setName((String) updates.get("name"));
        if (updates.containsKey("contactNumber")) user.setContactNumber((String) updates.get("contactNumber"));
        if (updates.containsKey("officialEmail")) user.setOfficialEmail((String) updates.get("officialEmail"));
        if (updates.containsKey("personalEmail")) user.setPersonalEmail((String) updates.get("personalEmail"));

        userRepository.save(user);
        return ResponseEntity.ok(buildCandidateMap(user));
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
        map.put("yop", u.getYop());
        return map;
    }
}
