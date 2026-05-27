package com.benchreadiness.interview.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Component
public class DocumentServiceClient {

    private final RestTemplate restTemplate;
    private final String documentServiceUrl;

    public DocumentServiceClient(@Value("${app.document-service.url}") String documentServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.documentServiceUrl = documentServiceUrl;
    }

    public String uploadDocument(byte[] fileBytes, String fileName) {
        String docId = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add("docId", docId);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    documentServiceUrl + "/documents/upload",
                    request,
                    Map.class
            );
            
            // Check if upload was accepted
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Document service upload failed with status: " + response.getStatusCode());
            }
            
            // Verify response contains docId
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !docId.equals(responseBody.get("docId"))) {
                throw new RuntimeException("Document service did not confirm docId in response");
            }
            
            return docId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload document to document service: " + e.getMessage(), e);
        }
    }

    public String queryDocument(String docId, String query, String systemPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("docId", docId);
        body.add("query", query);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.add("systemPrompt", systemPrompt);
        }

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                documentServiceUrl + "/documents/query",
                request,
                String.class
        );

        return response.getBody() != null ? response.getBody() : "";
    }
    
    /**
     * Check if a document exists and get its ingestion status.
     * @param docId the document identifier
     * @return Map with status info, or null if document doesn't exist
     */
    public Map<String, Object> getDocumentStatus(String docId) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    documentServiceUrl + "/documents/status/" + docId,
                    Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            // 404 or other errors mean document doesn't exist
            return null;
        }
    }
}
