package com.qb.core.controller;

import com.qb.client.AuthServiceClient;
import com.qb.core.dto.ApiResponse;
import com.qb.core.dto.CategoryDTO;
import com.qb.core.dto.CompanyDTO;
import com.qb.core.dto.TagDTO;
import com.qb.core.service.CategoryService;
import com.qb.core.service.CompanyService;
import com.qb.core.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified admin master-data API.
 * Lookup values (skill sets, statuses, etc.) are stored in auth-service;
 * question-bank entities (categories, tags, companies) are managed locally.
 */
@RestController
@RequestMapping("/api/admin/master-data")
@RequiredArgsConstructor
public class AdminMasterDataController {

    private final AuthServiceClient authServiceClient;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final CompanyService companyService;

    /** GET /api/admin/master-data — all lookup values + QB entities */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAll(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lookups", authServiceClient.getAllMasterData(includeInactive));
        result.put("categories", categoryService.getAllCategories());
        result.put("tags", tagService.getAllTags());
        result.put("companies", companyService.getAllCompanies());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** GET /api/admin/master-data/lookups/{category} — proxy to auth-service */
    @GetMapping("/lookups/{category}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLookups(
            @PathVariable String category,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.ok(
                authServiceClient.getMasterDataByCategory(category.toUpperCase(), includeInactive)));
    }

    @PostMapping("/lookups/{category}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createLookup(
            @PathVariable String category,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                authServiceClient.createMasterDataEntry(category.toUpperCase(), body)));
    }

    @PutMapping("/lookups/{category}/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateLookup(
            @PathVariable String category,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                authServiceClient.updateMasterDataEntry(category.toUpperCase(), id, body)));
    }

    @DeleteMapping("/lookups/{category}/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivateLookup(
            @PathVariable String category,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                authServiceClient.deactivateMasterDataEntry(category.toUpperCase(), id)));
    }

    // --- Question bank table-backed master data ---

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getAllCategories()));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryDTO>> createCategory(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String interviewType = body.getOrDefault("interviewType", "shared");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(categoryService.createCategory(name, interviewType)));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<CategoryDTO>> updateCategory(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        CategoryDTO updated = categoryService.renameCategory(id, name);
        if (body.containsKey("interviewType") && body.get("interviewType") != null) {
            updated = categoryService.updateInterviewType(id, body.get("interviewType"));
        }
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Category deleted"));
    }

    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<List<TagDTO>>> getTags() {
        return ResponseEntity.ok(ApiResponse.ok(tagService.getAllTags()));
    }

    @PostMapping("/tags")
    public ResponseEntity<ApiResponse<TagDTO>> createTag(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tag name is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(tagService.createTag(name)));
    }

    @PutMapping("/tags/{id}")
    public ResponseEntity<ApiResponse<TagDTO>> renameTag(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tag name is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(tagService.renameTag(id, name)));
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable UUID id) {
        tagService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tag deleted"));
    }

    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<List<CompanyDTO>>> getCompanies() {
        return ResponseEntity.ok(ApiResponse.ok(companyService.getAllCompanies()));
    }

    @PostMapping("/companies")
    public ResponseEntity<ApiResponse<CompanyDTO>> createCompany(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(companyService.createCompany(name)));
    }

    @PutMapping("/companies/{slug}")
    public ResponseEntity<ApiResponse<CompanyDTO>> renameCompany(
            @PathVariable String slug,
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(companyService.renameCompany(slug, name)));
    }

    @DeleteMapping("/companies/{slug}")
    public ResponseEntity<ApiResponse<Void>> deleteCompany(@PathVariable String slug) {
        companyService.deleteCompany(slug);
        return ResponseEntity.ok(ApiResponse.ok(null, "Company deleted"));
    }
}
