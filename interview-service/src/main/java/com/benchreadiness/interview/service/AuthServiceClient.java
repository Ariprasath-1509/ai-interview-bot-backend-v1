package com.benchreadiness.interview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AuthServiceClient {

    private final RestTemplate restTemplate;
    private final String authServiceUrl;

    public AuthServiceClient(RestTemplate restTemplate, 
                           @Value("${app.auth-service.url:http://localhost:8081}") String authServiceUrl) {
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
    }

    public Map<String, Object> getUserById(String userId) {
        try {
            return restTemplate.getForObject(authServiceUrl + "/auth/users/" + userId, Map.class);
        } catch (Exception e) {
            // Fallback to user ID if auth service is unavailable
            return Map.of(
                "id", userId,
                "name", "User " + userId.substring(0, Math.min(8, userId.length())),
                "email", userId + "@company.com"
            );
        }
    }
}