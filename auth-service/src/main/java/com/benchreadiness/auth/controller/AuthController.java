package com.benchreadiness.auth.controller;

import com.benchreadiness.auth.dto.CreateStaffRequest;
import com.benchreadiness.auth.dto.LoginRequest;
import com.benchreadiness.auth.dto.RegisterRequest;
import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.entity.UserRepository;
import com.benchreadiness.auth.entity.UserRole;
import com.benchreadiness.auth.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Set<UserRole> STAFF_ROLES =
        Set.of(UserRole.INTERVIEWER, UserRole.HR, UserRole.COMPLIANCE, UserRole.BENCH_MANAGER, UserRole.ADMIN);

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
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("ok", true, "message", "Registration successful. You can now log in."));
    }

    /** POST /auth/staff — BENCH_MANAGER creates a staff account */
    @PostMapping("/staff")
    public ResponseEntity<?> createStaff(@Valid @RequestBody CreateStaffRequest req,
                                          @RequestHeader("X-User-Role") String callerRole) {
        if (!callerRole.equals("BENCH_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "Only BENCH_MANAGER can create staff accounts"));
        }
        if (!STAFF_ROLES.contains(req.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Invalid staff role: " + req.getRole()));
        }
        if (req.getRole() == UserRole.CANDIDATE) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Cannot create CANDIDATE via staff endpoint"));
        }
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "error", "Email already registered"));
        }
        User user = new User();
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setPassword(req.getPassword());
        user.setRole(req.getRole());
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

        // Validate role matches what's stored — staff cannot impersonate other roles
        if (req.getRole() != null && req.getRole() != user.getRole()) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error",
                "Your account role is " + user.getRole().name() + ", not " + req.getRole().name()));
        }

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(Map.of("ok", true, "token", token, "role", user.getRole().name(), "name", user.getName() != null ? user.getName() : ""));
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
            .map(u -> ResponseEntity.ok(Map.of(
                "id", u.getId(),
                "name", u.getName() != null ? u.getName() : "",
                "email", u.getEmail(),
                "role", u.getRole().name()
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    /** GET /auth/candidates — search registered candidates */
    @GetMapping("/candidates")
    public ResponseEntity<?> getCandidates(@RequestParam(required = false, defaultValue = "") String search) {
        List<?> candidates = search.isBlank()
            ? userRepository.findByRole(UserRole.CANDIDATE)
            : userRepository.searchCandidates(search);
        return ResponseEntity.ok(candidates.stream()
            .map(u -> {
                User user = (User) u;
                return Map.of("id", user.getId(), "name", user.getName() != null ? user.getName() : "", "email", user.getEmail());
            })
            .toList());
    }

    /** GET /auth/staff — list all staff accounts (BENCH_MANAGER only) */
    @GetMapping("/staff")
    public ResponseEntity<?> listStaff(@RequestHeader("X-User-Role") String callerRole) {
        if (!callerRole.equals("BENCH_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        return ResponseEntity.ok(staff.stream()
            .map(u -> Map.of(
                "id", u.getId(),
                "name", u.getName() != null ? u.getName() : "",
                "email", u.getEmail(),
                "role", u.getRole().name()
            ))
            .toList());
    }

    /** GET /auth/admins — internal endpoint for digest service */
    @GetMapping("/admins")
    public ResponseEntity<?> listAdmins() {
        List<User> admins = userRepository.findByRoleIn(List.of(UserRole.ADMIN, UserRole.BENCH_MANAGER));
        return ResponseEntity.ok(admins.stream()
            .map(u -> Map.of("email", u.getEmail(), "name", u.getName() != null ? u.getName() : ""))
            .toList());
    }

    /** DELETE /auth/staff/{id} — BENCH_MANAGER removes a staff account */
    @DeleteMapping("/staff/{id}")
    public ResponseEntity<?> deleteStaff(@PathVariable String id,
                                          @RequestHeader("X-User-Role") String callerRole,
                                          @RequestHeader("X-User-Id") String callerId) {
        if (!callerRole.equals("BENCH_MANAGER")) {
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
        return ResponseEntity.ok(Map.of("id", userId, "role", role, "email", email));
    }
}
