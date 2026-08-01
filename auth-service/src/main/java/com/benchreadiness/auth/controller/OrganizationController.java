package com.benchreadiness.auth.controller;

import com.benchreadiness.auth.branch.BranchAccess;
import com.benchreadiness.auth.dto.CreateOrganizationRequest;
import com.benchreadiness.auth.dto.UpdateOrganizationRequest;
import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.entity.UserRole;
import com.benchreadiness.auth.feature.FeatureKey;
import com.benchreadiness.auth.feature.OrganizationFeatureService;
import com.benchreadiness.auth.org.Organization;
import com.benchreadiness.auth.org.OrganizationStatus;
import com.benchreadiness.auth.org.OrganizationType;
import com.benchreadiness.auth.repository.OrganizationRepository;
import com.benchreadiness.auth.repository.UserRepository;
import com.benchreadiness.auth.service.AuditService;
import com.benchreadiness.auth.service.PasswordService;
import com.benchreadiness.auth.util.PiiRedactor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** SUPER_ADMIN-only: create and manage organizations (tenants) — the top-level data-isolation boundary. */
@RestController
@RequestMapping("/auth/organizations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class OrganizationController {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final AuditService auditService;
    private final OrganizationFeatureService featureService;

    public OrganizationController(OrganizationRepository organizationRepository, UserRepository userRepository,
                                   PasswordService passwordService, AuditService auditService,
                                   OrganizationFeatureService featureService) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.auditService = auditService;
        this.featureService = featureService;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        List<Map<String, Object>> orgs = organizationRepository.findAll().stream()
                .map(this::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("organizations", orgs));
    }

    /** Creates the organization and its first org-admin user (ADMIN role, scoped to the new org) in one call. */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateOrganizationRequest req,
                                     @RequestHeader("X-User-Id") String callerId) {
        String code = req.getCode().trim().toUpperCase();
        if (!OrganizationType.isValid(req.getType())) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "type must be DEMO or LIVE"));
        }
        if (organizationRepository.existsByCode(code)) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "error", "Organization code already exists"));
        }
        if (userRepository.findByEmailIgnoreCase(req.getAdminEmail().trim()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "error", "Email already registered"));
        }

        Organization org = new Organization();
        org.setCode(code);
        org.setName(req.getName().trim());
        org.setType(OrganizationType.valueOf(req.getType().trim().toUpperCase()));
        org.setMaxInterviews(req.getMaxInterviews());
        org.setMaxCandidates(req.getMaxCandidates());
        org.setMaxClients(req.getMaxClients());
        organizationRepository.save(org);

        User admin = new User();
        admin.setName(req.getAdminName().trim());
        admin.setEmail(req.getAdminEmail().trim().toLowerCase());
        admin.setPassword(passwordService.encode(req.getAdminPassword()));
        admin.setRole(UserRole.ADMIN);
        admin.setBranch(BranchAccess.defaultBranch());
        admin.setOrgCode(code);
        userRepository.save(admin);

        auditService.record(callerId, null, "SUPER_ADMIN", "ORGANIZATION_CREATED", "ORGANIZATION", org.getId(),
                String.format("Created org %s (%s), type=%s, admin=%s", org.getName(), org.getCode(),
                        org.getType(), PiiRedactor.maskEmail(admin.getEmail())));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("organization", toMap(org));
        response.put("adminUserId", admin.getId());
        return ResponseEntity.ok(response);
    }

    /** Partial update — adjust type, status, or demo limits. Pass clearMaxX=true to remove a cap (unlimited). */
    @PatchMapping("/{code}")
    public ResponseEntity<?> update(@PathVariable String code, @RequestBody UpdateOrganizationRequest req,
                                     @RequestHeader("X-User-Id") String callerId) {
        Organization org = organizationRepository.findByCode(code.trim().toUpperCase()).orElse(null);
        if (org == null) {
            return ResponseEntity.notFound().build();
        }
        if (req.getType() != null) {
            if (!OrganizationType.isValid(req.getType())) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "type must be DEMO or LIVE"));
            }
            org.setType(OrganizationType.valueOf(req.getType().trim().toUpperCase()));
        }
        if (req.getStatus() != null) {
            try {
                org.setStatus(OrganizationStatus.valueOf(req.getStatus().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "status must be ACTIVE or SUSPENDED"));
            }
        }
        if (req.isClearMaxInterviews()) org.setMaxInterviews(null);
        else if (req.getMaxInterviews() != null) org.setMaxInterviews(req.getMaxInterviews());
        if (req.isClearMaxCandidates()) org.setMaxCandidates(null);
        else if (req.getMaxCandidates() != null) org.setMaxCandidates(req.getMaxCandidates());
        if (req.isClearMaxClients()) org.setMaxClients(null);
        else if (req.getMaxClients() != null) org.setMaxClients(req.getMaxClients());

        organizationRepository.save(org);

        auditService.record(callerId, null, "SUPER_ADMIN", "ORGANIZATION_UPDATED", "ORGANIZATION", org.getId(),
                String.format("Updated org %s: type=%s, status=%s, maxInterviews=%s, maxCandidates=%s, maxClients=%s",
                        org.getCode(), org.getType(), org.getStatus(), org.getMaxInterviews(), org.getMaxCandidates(), org.getMaxClients()));

        return ResponseEntity.ok(Map.of("ok", true, "organization", toMap(org)));
    }

    /** Refuses to delete while any user (staff or candidate) still references this org — orgCode is a
     * denormalized string across every service's tables, not a cascading FK, so deleting the org row
     * out from under existing users would leave their data pointing at a tenant that no longer exists. */
    @DeleteMapping("/{code}")
    public ResponseEntity<?> delete(@PathVariable String code, @RequestHeader("X-User-Id") String callerId) {
        String orgCode = code.trim().toUpperCase();
        Organization org = organizationRepository.findByCode(orgCode).orElse(null);
        if (org == null) {
            return ResponseEntity.notFound().build();
        }
        if (userRepository.existsByOrgCodeIgnoreCase(orgCode)) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "error",
                    "Cannot delete an organization with existing staff or candidates. Remove or reassign its users first, "
                            + "or suspend it instead (Status: Suspended) to block access without deleting data."));
        }

        organizationRepository.delete(org);

        auditService.record(callerId, null, "SUPER_ADMIN", "ORGANIZATION_DELETED", "ORGANIZATION", org.getId(),
                String.format("Deleted org %s (%s)", org.getName(), org.getCode()));

        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Which features this org is entitled to use (missing key = enabled by default). */
    @GetMapping("/{code}/features")
    public ResponseEntity<?> getFeatures(@PathVariable String code) {
        String orgCode = code.trim().toUpperCase();
        if (!organizationRepository.existsByCode(orgCode)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("orgCode", orgCode, "features", featureService.getFeatureStates(orgCode)));
    }

    /** Body: { "CANDIDATES": false, "ANALYTICS": true, ... } — only listed keys are changed. */
    @PutMapping("/{code}/features")
    public ResponseEntity<?> updateFeatures(@PathVariable String code, @RequestBody Map<String, Boolean> updates,
                                             @RequestHeader("X-User-Id") String callerId) {
        String orgCode = code.trim().toUpperCase();
        if (!organizationRepository.existsByCode(orgCode)) {
            return ResponseEntity.notFound().build();
        }
        Map<FeatureKey, Boolean> parsed = new EnumMap<>(FeatureKey.class);
        for (Map.Entry<String, Boolean> entry : updates.entrySet()) {
            try {
                parsed.put(FeatureKey.valueOf(entry.getKey().trim().toUpperCase()), entry.getValue());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Unknown feature key: " + entry.getKey()));
            }
        }
        featureService.setFeatures(orgCode, parsed);

        auditService.record(callerId, null, "SUPER_ADMIN", "ORGANIZATION_FEATURES_UPDATED", "ORGANIZATION", orgCode,
                String.format("Updated features for org %s: %s", orgCode, parsed));

        return ResponseEntity.ok(Map.of("ok", true, "orgCode", orgCode, "features", featureService.getFeatureStates(orgCode)));
    }

    private Map<String, Object> toMap(Organization org) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", org.getId());
        m.put("code", org.getCode());
        m.put("name", org.getName());
        m.put("type", org.getType().name());
        m.put("status", org.getStatus().name());
        m.put("maxInterviews", org.getMaxInterviews());
        m.put("maxCandidates", org.getMaxCandidates());
        m.put("maxClients", org.getMaxClients());
        m.put("createdAt", org.getCreatedAt() != null ? org.getCreatedAt().toString() : null);
        return m;
    }
}
