package com.benchreadiness.compliance.controller;

import com.benchreadiness.compliance.dto.LlmConfigRequest;
import com.benchreadiness.compliance.dto.LlmConfigResponse;
import com.benchreadiness.compliance.service.LlmConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/llm")
public class LlmConfigurationController {

    private final LlmConfigurationService service;

    public LlmConfigurationController(LlmConfigurationService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public ResponseEntity<LlmConfigResponse> getConfig() {
        return ResponseEntity.ok(service.getCurrentConfig());
    }

    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchProvider(
            @RequestBody LlmConfigRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {

        if (!"SUPER_ADMIN".equals(userRole)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only SUPER_ADMIN can change LLM configuration"));
        }

        LlmConfigResponse oldConfig = service.getCurrentConfig();
        LlmConfigResponse newConfig = service.switchProvider(request, userId, userEmail, userRole);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("previousProvider", oldConfig.getProvider());
        response.put("currentProvider", newConfig.getProvider());
        response.put("message", "LLM provider switched successfully");
        response.put("updatedAt", newConfig.getUpdatedAt());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/available-models")
    public ResponseEntity<Map<String, List<String>>> getAvailableModels() {
        Map<String, List<String>> models = new HashMap<>();
        
        models.put("claude", Arrays.asList(
                "claude-haiku-4-5",
                "claude-sonnet-4-5",
                "claude-opus-4-5"
        ));
        
        models.put("ollama", Arrays.asList(
                "qwen2.5:7b",
                "qwen2.5:14b",
                "qwen2.5:32b",
                "llama3.1:8b",
                "llama3.1:70b",
                "mistral:7b"
        ));
        
        return ResponseEntity.ok(models);
    }
}
