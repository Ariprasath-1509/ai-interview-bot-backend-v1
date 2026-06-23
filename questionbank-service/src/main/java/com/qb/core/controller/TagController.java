package com.qb.core.controller;

import com.qb.core.dto.ApiResponse;
import com.qb.core.dto.TagDTO;
import com.qb.core.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TagDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(tagService.getAllTags()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TagDTO>> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tag name is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(tagService.createTag(name)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        tagService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tag deleted"));
    }
}
