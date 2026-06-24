package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.benchreadiness.ai.dto.MatchingRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiMatchingService {

    private static final Logger log = LoggerFactory.getLogger(AiMatchingService.class);

    private final LlmClient llmClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final ObjectMapper objectMapper;

    public AiMatchingService(LlmClient llmClient, ComplianceServiceClient complianceServiceClient,
                             ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.complianceServiceClient = complianceServiceClient;
        this.objectMapper = objectMapper;
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
            log.error("AI matching failed: {}", e.getMessage());
            return fallbackMatching(request);
        }
    }

    private String buildMatchingPrompt(MatchingRequest request) {
        return """
            You are a highly strict technical recruitment filter ranking candidate text packages against a target Client Job Description.
            You must base your deductions exclusively on the candidate JSON properties provided. Do NOT assume, infer, or hallucinate skills.
            
            TARGET SYSTEM SCOPE:
            - Client Target Name: %s
            - Target Profile Designation: %s
            - Client Platform Source: %s
            - Complete JD Profile Text: %s
            - Maximum Allowed Output Count: %d
            
            STRICT ALGORITHM EVALUATION RULESET:
            1. Skill Weight (30%% Max Value): Prioritize 'interviewEvidence' metrics over raw resume claims. Deduct heavily if modern structural frameworks (e.g., distributed architectures, microservices) are reduced down to simple basic CRUD interactions.
            2. Experience Weight (25%% Max Value): Evaluate the 'yoeForMatching' metric. If it sits 20-30%% below requirements, enforce a strong penalty. If it falls greater than 30%% below, implement a severe score reduction.
            3. Ownership Weight (20%% Max Value): Lower scores if the candidate profile reflects only maintenance or support tasks rather than feature design ownership.
            4. Stability Weight (15%% Max Value): Apply a strong penalty if 'systemInterviewCount' is greater than or equal to 8, or if 'noOfInterviews' is greater than or equal to 12.
            5. Performance Weight (10%% Max Value): Extract 'avgInterviewScore'. Strong communication skills must NOT mask technical gaps.
            
            SCORE ALIGNMENT LIMITATIONS:
            - Score >= 0.85: HIGHLY_RECOMMENDED (Exceptional match with minimal gaps).
            - Score 0.70 - 0.84: RECOMMENDED (Strong fit with manageable gaps).
            - Score 0.55 - 0.69: CONSIDER (Partial fit).
            - Score < 0.55: NOT_SUITABLE (Weak fit or major structural discrepancies).
            
            CRITICAL MATCH SAFETY BARRIERS:
            - If a candidate falls below the required YOE and lacks system architecture depth, their total final score CANNOT cross a maximum of 0.60.
            - Missing 2 or more core JD requirements automatically caps the final score at a maximum of 0.55.
            - Resume claims alone cannot produce a score above 0.70 unless interview evidence validates the claim.
            
            OUTPUT SPECIFICATION:
            Generate ONLY the raw JSON format string containing the 'matches' collection array mapped in best-first descending order, alongside the system numerical match summary block. No markdown enclosing frames.
            
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
            """.formatted(
                request.getClientName(),
                request.getJdTitle(),
                request.getSource(),
                request.getJdDescription(),
                request.getMaxCandidates()
            );
    }

    private Map<String, Object> parseMatchingResponse(String response, MatchingRequest request) {
        try {
            response = JsonRepairUtil.repair(response);
            Map<String, Object> result = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            result.put("source", "ai-ollama");
            result.put("clientId", request.getClientId());
            return result;
        } catch (Exception e) {
            log.error("Failed to parse AI matching response: {} — preview: {}", e.getMessage(),
                response.substring(0, Math.min(200, response.length())));
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
            c.put("systemInterviewCount", candidate.get("systemInterviewCount"));
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