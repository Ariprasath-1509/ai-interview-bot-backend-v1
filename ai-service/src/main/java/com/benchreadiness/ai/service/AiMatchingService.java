package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.benchreadiness.ai.dto.MatchingRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiMatchingService {

    private final LlmClient llmClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final ObjectMapper objectMapper;

    public AiMatchingService(LlmClient llmClient, ComplianceServiceClient complianceServiceClient) {
        this.llmClient = llmClient;
        this.complianceServiceClient = complianceServiceClient;
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> matchCandidates(MatchingRequest request, String userId) {
        try {
            if (request.getCandidates() == null || request.getCandidates().isEmpty()) {
                return Map.of(
                    "matches", List.of(),
                    "summary", Map.of("totalCandidatesAnalyzed", 0),
                    "source", "empty-input",
                    "clientId", request.getClientId()
                );
            }

            if (!llmClient.isConfigured()) {
                return fallbackMatching(request);
            }

            String prompt = buildMatchingPrompt(request);
            List<Map<String, Object>> compactCandidates = compactCandidates(request.getCandidates());
            String candidatesJson = objectMapper.writeValueAsString(compactCandidates);
            
            String response = llmClient.chatMatching(prompt, candidatesJson);
            
            // Track tokens - skip for matching since we don't have interviewId
            // Token tracking is optional for matching operations
            
            return parseMatchingResponse(response, request);
            
        } catch (Exception e) {
            System.err.println("AI matching failed: " + e.getMessage());
            return fallbackMatching(request);
        }
    }

    private String buildMatchingPrompt(MatchingRequest request) {
        String jdDigest = truncate(request.getJdDescription(), 1000);
        return """
            You are a technical recruiter ranking candidates against a JD.
            Use only the candidate JSON provided in the user message.

            CLIENT:
            - Name: %s
            - Role: %s
            - Source: %s
            - JD digest: %s

            RULES:
            - Use yoeForMatching for experience comparison.
            - Use interviewEvidence as primary truth (resume can support context).
            - Weights: skill 30%%, experience 25%%, role complexity 20%%, quality 15%%, interview performance 10%%.
            - Penalize very high interview frequency (>= 7 progressively; >=20 severe).
            - Return at most maxCandidates in best-first order.

            Return ONLY valid JSON with this structure:

            {
              "matches": [
                {
                  "candidateId": "string",
                  "candidateName": "string",
                  "candidateEmail": "string",
                  "matchScore": 0.85,
                  "recommendation": "HIGHLY_RECOMMENDED",
                  "skillAlignment": {
                    "score": 0.25,
                    "candidateSkillSet": "JAVA_SB",
                    "analysis": "Short explanation of skill fit using interviewEvidence and resume"
                  },
                  "experienceAlignment": {
                    "score": 0.20,
                    "requiredYoe": 5.0,
                    "candidateYoe": 5.5,
                    "analysis": "Short explanation of yoeForMatching fit"
                  },
                  "strengths": [
                    "Top 2-3 strengths"
                  ],
                  "concerns": [
                    "Top 1-3 concerns"
                  ],
                  "qualityIndicators": {
                    "rating": "ASSET",
                    "readinessStatus": "RFD", 
                    "avgInterviewScore": 4.2,
                    "interviewCount": 3
                  }
                }
              ],
              "summary": {
                "totalCandidatesAnalyzed": 8,
                "highlyRecommended": 2,
                "recommended": 3,
                "consider": 2,
                "notSuitable": 1,
                "topMatchScore": 0.85,
                "averageMatchScore": 0.62
              }
            }

            No markdown. No prose outside JSON.
            """.formatted(
                request.getClientName(),
                request.getJdTitle(), 
                request.getSource(),
                jdDigest
            );
    }

    private Map<String, Object> parseMatchingResponse(String response, MatchingRequest request) {
        try {
            // Clean response
            response = response.trim();
            
            // Remove markdown code blocks
            if (response.startsWith("```json")) {
                response = response.substring(7);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            
            // Find JSON content if response contains explanatory text
            int jsonStart = response.indexOf('{');
            int jsonEnd = response.lastIndexOf('}');
            
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                response = response.substring(jsonStart, jsonEnd + 1);
            }
            
            response = response.trim();
            
            // Validate it looks like JSON
            if (!response.startsWith("{") || !response.endsWith("}")) {
                System.err.println("Response doesn't look like JSON, using fallback. Response starts with: " + 
                    response.substring(0, Math.min(100, response.length())));
                return fallbackMatching(request);
            }

            Map<String, Object> result = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            result.put("source", "ai-claude");
            result.put("clientId", request.getClientId());
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Failed to parse AI matching response: " + e.getMessage());
            System.err.println("Response preview: " + response.substring(0, Math.min(200, response.length())));
            return fallbackMatching(request);
        }
    }

    private Map<String, Object> fallbackMatching(MatchingRequest request) {
        // Simple fallback when AI is unavailable
        List<Map<String, Object>> matches = new ArrayList<>();
        
        for (Map<String, Object> candidate : request.getCandidates()) {
            Map<String, Object> match = new HashMap<>();
            match.put("candidateId", candidate.get("id"));
            match.put("candidateName", candidate.get("name"));
            match.put("matchScore", 0.5); // Neutral score
            match.put("recommendation", "CONSIDER");
            match.put("strengths", List.of("Candidate available for review"));
            match.put("concerns", List.of("AI matching unavailable - manual review required"));
            matches.add(match);
        }
        
        return Map.of(
            "matches", matches.subList(0, Math.min(matches.size(), request.getMaxCandidates())),
            "summary", Map.of(
                "totalCandidatesAnalyzed", request.getCandidates().size(),
                "source", "fallback-algorithm"
            ),
            "source", "fallback",
            "clientId", request.getClientId()
        );
    }

    private int estimateTokens(String text) {
        // Rough estimation: 1 token ≈ 4 characters
        return text.length() / 4;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> compactCandidates(List<Map<String, Object>> candidates) {
        List<Map<String, Object>> compact = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", candidate.get("id"));
            c.put("name", candidate.get("name"));
            c.put("email", candidate.get("email"));
            c.put("officialEmail", candidate.get("officialEmail"));
            c.put("personalEmail", candidate.get("personalEmail"));
            c.put("skillSet", candidate.get("skillSet"));
            c.put("rating", candidate.get("rating"));
            c.put("candidateStatus", candidate.get("candidateStatus"));
            c.put("yoeForMatching", candidate.get("yoeForMatching"));
            c.put("yoeActual", candidate.get("yoeActual"));
            c.put("noOfInterviews", candidate.get("noOfInterviews"));
            c.put("resumeSummary", truncate((String) candidate.get("resumeSummary"), 280));

            Object evidenceObj = candidate.get("interviewEvidence");
            if (evidenceObj instanceof Map<?, ?> evidenceRaw) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("recentInterviewCount", evidenceRaw.get("recentInterviewCount"));
                evidence.put("averageScore", evidenceRaw.get("averageScore"));
                evidence.put("categoryScores", evidenceRaw.get("categoryScores"));
                evidence.put("strengths", limitStringList((List<Object>) evidenceRaw.get("strengths"), 3, 120));
                evidence.put("weaknesses", limitStringList((List<Object>) evidenceRaw.get("weaknesses"), 3, 120));
                c.put("interviewEvidence", evidence);
            }

            compact.add(c);
        }
        return compact;
    }

    private List<String> limitStringList(List<Object> values, int limit, int maxLen) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object value : values) {
            if (value == null) continue;
            out.add(truncate(String.valueOf(value), maxLen));
            if (out.size() >= limit) break;
        }
        return out;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "";
        String clean = value.trim().replaceAll("\\s+", " ");
        if (clean.length() <= maxLen) return clean;
        return clean.substring(0, maxLen) + "...";
    }
}