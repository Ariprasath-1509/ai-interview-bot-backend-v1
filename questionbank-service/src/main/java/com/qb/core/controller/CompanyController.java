package com.qb.core.controller;

import com.qb.core.dto.ApiResponse;
import com.qb.core.dto.CompanyDTO;
import com.qb.core.dto.QuestionDTO;
import com.qb.core.service.CompanyService;
import com.qb.core.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final QuestionService questionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(companyService.getAllCompanies()));
    }

    @GetMapping("/{slug}/questions")
    public ResponseEntity<ApiResponse<List<QuestionDTO>>> getQuestionsByCompany(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(questionService.getByCompanySlug(slug)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyDTO>> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(companyService.createCompany(name)));
    }

    @PutMapping("/{slug}")
    public ResponseEntity<ApiResponse<CompanyDTO>> rename(
            @PathVariable String slug,
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(companyService.renameCompany(slug, name)));
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        companyService.deleteCompany(slug);
        return ResponseEntity.ok(ApiResponse.ok(null, "Company deleted"));
    }
}
