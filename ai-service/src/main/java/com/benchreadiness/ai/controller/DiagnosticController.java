package com.benchreadiness.ai.controller;

import com.benchreadiness.ai.service.LlmClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai/diagnostic")
public class DiagnosticController {

    private final LlmClient llmClient;

    public DiagnosticController(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @GetMapping("/llm-test")
    public ResponseEntity<?> testLlm() {
        try {
            if (!llmClient.isConfigured()) {
                return ResponseEntity.ok(Map.of(
                    "configured", false,
                    "message", "LLM provider not configured"
                ));
            }
            
            String response = llmClient.chatQuestion(
                "You are a test assistant. Respond with exactly: {\"test\": \"success\"}",
                "Test message"
            );
            
            return ResponseEntity.ok(Map.of(
                "configured", true,
                "testSuccessful", true,
                "response", response
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "configured", true,
                "testSuccessful", false,
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }
}