package com.benchreadiness.ai.controller;

import com.benchreadiness.ai.service.ClaudeAiClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai/diagnostic")
public class DiagnosticController {

    private final ClaudeAiClient claudeAiClient;

    public DiagnosticController(ClaudeAiClient claudeAiClient) {
        this.claudeAiClient = claudeAiClient;
    }

    @GetMapping("/claude-test")
    public ResponseEntity<?> testClaude() {
        try {
            if (!claudeAiClient.isConfigured()) {
                return ResponseEntity.ok(Map.of(
                    "configured", false,
                    "message", "Claude API key not configured"
                ));
            }
            
            String response = claudeAiClient.chatQuestion(
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