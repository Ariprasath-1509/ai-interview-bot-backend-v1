package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.security.StaffSecurityRoles;
import com.benchreadiness.interview.client.DocumentServiceClient;
import com.benchreadiness.interview.feature.FeatureKey;
import com.benchreadiness.interview.feature.RequiresFeature;
import com.benchreadiness.interview.service.ClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/recruiter/bot")
public class RecruiterBotController {

    private final ClientService clientService;
    private final DocumentServiceClient documentServiceClient;

    public RecruiterBotController(ClientService clientService, DocumentServiceClient documentServiceClient) {
        this.clientService = clientService;
        this.documentServiceClient = documentServiceClient;
    }

    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    @RequiresFeature(FeatureKey.RECRUITER_BOT)
    public ResponseEntity<Map<String, String>> queryBot(@RequestBody Map<String, String> request,
                                                        @RequestHeader("X-User-Role") String userRole) {
        String clientId = request.get("clientId");
        String query = request.get("query");
        String systemPrompt = request.get("systemPrompt");

        if (clientId == null || query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId and query are required"));
        }

        String docId;
        try {
            docId = clientService.getDocId(UUID.fromString(clientId), userRole);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid client ID: " + e.getMessage()));
        }

        if (docId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No JD document uploaded for this client"));
        }

        try {
            String response = documentServiceClient.queryDocument(docId, query, systemPrompt);
            return ResponseEntity.ok(Map.of("response", response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Document service error: " + e.getMessage()));
        }
    }
}
