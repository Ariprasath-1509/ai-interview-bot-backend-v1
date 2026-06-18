package com.benchreadiness.auth.masterdata;

import com.benchreadiness.auth.masterdata.dto.CreateMasterDataRequest;
import com.benchreadiness.auth.masterdata.dto.MasterDataEntryDTO;
import com.benchreadiness.auth.masterdata.dto.UpdateMasterDataRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth/master-data")
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    /** GET /auth/master-data — all lookup values grouped by category (for dropdowns) */
    @GetMapping
    public ResponseEntity<Map<String, List<MasterDataEntryDTO>>> getAll(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(masterDataService.listAllGrouped(includeInactive));
    }

    /** GET /auth/master-data/categories — list of category keys */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> listCategories() {
        return ResponseEntity.ok(List.of(
                MasterDataCategory.SKILL_SET,
                MasterDataCategory.CANDIDATE_STATUS,
                MasterDataCategory.CANDIDATE_SOURCE,
                MasterDataCategory.CANDIDATE_RATING,
                MasterDataCategory.ADMIN_SOURCE,
                MasterDataCategory.INTERVIEW_ROUND,
                MasterDataCategory.QUESTION_TYPE,
                MasterDataCategory.DIFFICULTY,
                MasterDataCategory.CATEGORY_INTERVIEW_TYPE,
                MasterDataCategory.POSITION_SOURCE,
                MasterDataCategory.BRANCH
        ));
    }

    /** GET /auth/master-data/{category} */
    @GetMapping("/{category}")
    public ResponseEntity<List<MasterDataEntryDTO>> listByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(masterDataService.listByCategory(category.toUpperCase(), includeInactive));
    }

    /** GET /auth/master-data/{category}/{code} */
    @GetMapping("/{category}/{code}")
    public ResponseEntity<MasterDataEntryDTO> getByCode(
            @PathVariable String category,
            @PathVariable String code) {
        return ResponseEntity.ok(masterDataService.getByCategoryAndCode(category.toUpperCase(), code));
    }

    /** POST /auth/master-data/{category} — SUPER_ADMIN creates a new lookup value */
    @PostMapping("/{category}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'TESTING_ADMIN')")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String category,
            @Valid @RequestBody CreateMasterDataRequest request) {
        MasterDataEntryDTO created = masterDataService.create(category.toUpperCase(), request);
        return ResponseEntity.ok(Map.of("ok", true, "entry", created));
    }

    /** PUT /auth/master-data/{category}/{id} — SUPER_ADMIN updates label/order/active/metadata */
    @PutMapping("/{category}/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'TESTING_ADMIN')")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String category,
            @PathVariable UUID id,
            @RequestBody UpdateMasterDataRequest request) {
        MasterDataEntryDTO updated = masterDataService.update(category.toUpperCase(), id, request);
        return ResponseEntity.ok(Map.of("ok", true, "entry", updated));
    }

    /** DELETE /auth/master-data/{category}/{id} — soft-deactivate (preserves historical references) */
    @DeleteMapping("/{category}/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'TESTING_ADMIN')")
    public ResponseEntity<Map<String, Object>> deactivate(
            @PathVariable String category,
            @PathVariable UUID id) {
        masterDataService.deactivate(category.toUpperCase(), id);
        return ResponseEntity.ok(Map.of("ok", true, "message", "Entry deactivated"));
    }
}
