package com.benchreadiness.ai.controller;

import com.benchreadiness.ai.service.OpenAiClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai/diagnostic")
public class DiagnosticController {

    private final OpenAiClient openAiClient;

    public DiagnosticController(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    @GetMapping("/claude-test")
    public ResponseEntity<?> testClaude() {
        try {
            if (!openAiClient.isConfigured()) {
                return ResponseEntity.ok(Map.of(
                    "configured", false,
                    "message", "Claude API key not configured"
                ));
            }
            
            String response = openAiClient.chatQuestion(
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