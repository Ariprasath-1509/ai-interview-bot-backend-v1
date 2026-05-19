package com.benchreadiness.compliance.service;

import com.benchreadiness.compliance.dto.AuditLogRequest;
import com.benchreadiness.compliance.dto.LlmConfigRequest;
import com.benchreadiness.compliance.dto.LlmConfigResponse;
import com.benchreadiness.compliance.entity.LlmConfiguration;
import com.benchreadiness.compliance.repository.LlmConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class LlmConfigurationService {

    private final LlmConfigurationRepository repository;
    private final ComplianceService complianceService;

    public LlmConfigurationService(LlmConfigurationRepository repository, ComplianceService complianceService) {
        this.repository = repository;
        this.complianceService = complianceService;
    }

    public LlmConfigResponse getCurrentConfig() {
        try {
            LlmConfiguration config = repository.findById(1L)
                    .orElseThrow(() -> new RuntimeException("LLM configuration not found"));
            return toResponse(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch LLM configuration: " + e.getMessage(), e);
        }
    }

    @Transactional
    public LlmConfigResponse switchProvider(LlmConfigRequest request, Long userId, String userName, String userRole) {
        LlmConfiguration config = repository.findById(1L)
                .orElseThrow(() -> new RuntimeException("LLM configuration not found"));

        String oldProvider = config.getProvider();
        String oldConfig = buildConfigString(config);

        config.setProvider(request.getProvider());
        config.setUpdatedBy(userId);

        if (request.getClaudeModels() != null) {
            config.setClaudeQuestionModel(request.getClaudeModels().get("question"));
            config.setClaudeAssessmentModel(request.getClaudeModels().get("assessment"));
            config.setClaudeRubricModel(request.getClaudeModels().get("rubric"));
            config.setClaudeMatchingModel(request.getClaudeModels().get("matching"));
        }

        if (request.getOllamaModels() != null) {
            config.setOllamaQuestionModel(request.getOllamaModels().get("question"));
            config.setOllamaAssessmentModel(request.getOllamaModels().get("assessment"));
            config.setOllamaRubricModel(request.getOllamaModels().get("rubric"));
            config.setOllamaMatchingModel(request.getOllamaModels().get("matching"));
        }

        LlmConfiguration saved = repository.save(config);

        // Audit log
        AuditLogRequest auditLog = new AuditLogRequest();
        auditLog.setActorId(String.valueOf(userId));
        auditLog.setActorName(userName);
        auditLog.setActorRole(userRole);
        auditLog.setAction("LLM_CONFIG_CHANGED");
        auditLog.setResource("llm_configuration");
        auditLog.setResourceId("1");
        auditLog.setDetail("Switched LLM provider from " + oldProvider + " to " + request.getProvider());
        auditLog.setOldValue(oldConfig);
        auditLog.setNewValue(buildConfigString(saved));
        complianceService.record(auditLog);

        return toResponse(saved);
    }

    private LlmConfigResponse toResponse(LlmConfiguration config) {
        LlmConfigResponse response = new LlmConfigResponse();
        response.setProvider(config.getProvider());
        response.setUpdatedAt(config.getUpdatedAt());

        Map<String, String> claudeModels = new HashMap<>();
        claudeModels.put("question", config.getClaudeQuestionModel());
        claudeModels.put("assessment", config.getClaudeAssessmentModel());
        claudeModels.put("rubric", config.getClaudeRubricModel());
        claudeModels.put("matching", config.getClaudeMatchingModel());
        response.setClaudeModels(claudeModels);

        Map<String, String> ollamaModels = new HashMap<>();
        ollamaModels.put("question", config.getOllamaQuestionModel());
        ollamaModels.put("assessment", config.getOllamaAssessmentModel());
        ollamaModels.put("rubric", config.getOllamaRubricModel());
        ollamaModels.put("matching", config.getOllamaMatchingModel());
        response.setOllamaModels(ollamaModels);

        return response;
    }

    private String buildConfigString(LlmConfiguration config) {
        return String.format("Provider: %s, Claude: [Q:%s, A:%s, R:%s, M:%s], Ollama: [Q:%s, A:%s, R:%s, M:%s]",
                config.getProvider(),
                config.getClaudeQuestionModel(), config.getClaudeAssessmentModel(),
                config.getClaudeRubricModel(), config.getClaudeMatchingModel(),
                config.getOllamaQuestionModel(), config.getOllamaAssessmentModel(),
                config.getOllamaRubricModel(), config.getOllamaMatchingModel());
    }
}
