package com.qb.core.controller;

import com.qb.core.dto.ApiResponse;
import com.qb.core.dto.CategoryDTO;
import com.qb.core.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getAllCategories()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryDTO>> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String interviewType = body.getOrDefault("interviewType", "shared");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(categoryService.createCategory(name, interviewType)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryDTO>> rename(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(categoryService.renameCategory(id, name)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Category deleted. Questions reassigned to General."));
    }
}
