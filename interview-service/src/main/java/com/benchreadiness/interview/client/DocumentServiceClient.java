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

        restTemplate.postForEntity(
                documentServiceUrl + "/documents/upload",
                request,
                Map.class
        );

        return docId;
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
}
