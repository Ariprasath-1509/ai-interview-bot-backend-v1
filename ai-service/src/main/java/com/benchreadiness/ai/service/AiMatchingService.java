package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.benchreadiness.ai.dto.MatchingRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiMatchingService {

    private final ClaudeAiClient claudeAiClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final ObjectMapper objectMapper;

    public AiMatchingService(ClaudeAiClient claudeAiClient, ComplianceServiceClient complianceServiceClient) {
        this.claudeAiClient = claudeAiClient;
        this.complianceServiceClient = complianceServiceClient;
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> matchCandidates(MatchingRequest request, String userId) {
        try {
            if (!claudeAiClient.isConfigured()) {
                return fallbackMatching(request);
            }

            String prompt = buildMatchingPrompt(request);
            String candidatesJson = objectMapper.writeValueAsString(request.getCandidates());
            
            String response = claudeAiClient.chatMatching(prompt, candidatesJson);
            
            // Track tokens - skip for matching since we don't have interviewId
            // Token tracking is optional for matching operations
            
            return parseMatchingResponse(response, request);
            
        } catch (Exception e) {
            System.err.println("AI matching failed: " + e.getMessage());
            return fallbackMatching(request);
        }
    }

    private String buildMatchingPrompt(MatchingRequest request) {
        return """
            You are an expert technical recruiter with deep knowledge of software engineering roles and candidate assessment. 
            Your task is to match candidates to a client's job requirements using sophisticated analysis.

            ## CLIENT REQUIREMENTS
            **Client:** %s
            **Role:** %s
            **Job Description:** %s
            **Source:** %s

            ## CANDIDATE ELIGIBILITY
            **NOTE:** All candidates provided have already been pre-filtered to meet minimum criteria:
            - Status: RFD (Ready for Deployment)
            - Interview History: 3+ completed interviews in our system
            - Each candidate includes REAL INTERVIEW EVIDENCE from their recent 3 interviews

            ## MATCHING CRITERIA & WEIGHTS

            ### 1. TECHNICAL SKILL ALIGNMENT (30%% weight)
            - **Perfect Match (0.30):** Candidate's primary skillSet directly matches JD requirements
              - JAVA_SB → Java/Spring Boot/Microservices roles
              - JFSR → Full-stack development roles  
              - REACT_JS → Frontend/React roles
            - **Good Match (0.20-0.25):** Transferable skills with some overlap
            - **Partial Match (0.10-0.15):** Some relevant skills but significant gaps
            - **Poor Match (0.05):** Minimal skill alignment
            - **USE interviewEvidence.categoryScores** to validate claimed skills with actual performance

            ### 2. EXPERIENCE LEVEL ALIGNMENT (25%% weight)
            - Extract required years from JD (look for "X+ years", "X years", role seniority indicators)
            - **Perfect (0.25):** Candidate YOE within ±0.5 years of requirement
            - **Close (0.20):** Within ±1 year
            - **Acceptable (0.15):** Within ±2 years
            - **Gap (0.10):** Within ±3 years
            - **Mismatch (0.05):** >3 years difference
            - **Under-qualification penalty:** -30%% if candidate significantly under-qualified

            ### 3. ROLE COMPLEXITY MATCH (20%% weight)
            - **Senior/Lead roles:** Require 4+ years, penalize if candidate <4 years
            - **Principal/Staff roles:** Require 7+ years, penalize if candidate <7 years
            - **Junior roles:** 0-3 years ideal
            - **Mid-level roles:** 2-5 years ideal

            ### 4. CANDIDATE QUALITY INDICATORS (15%% weight)
            - **Rating Impact:**
              - ASSET: +0.15 (high-performing candidate)
              - MEDIUM: +0.08 (average performer)
              - LIABILITY: -0.05 (performance concerns)
            - **Readiness Status:**
              - RFD (Ready for Deployment): +0.10 (all candidates are RFD)
            - **USE interviewEvidence.strengths** to identify proven capabilities
            - **USE interviewEvidence.weaknesses** to identify skill gaps

            ### 5. INTERVIEW PERFORMANCE HISTORY (10%% weight)
            - **Excellent (4.5+ avg):** +0.10
            - **Good (4.0-4.4 avg):** +0.08
            - **Average (3.5-3.9 avg):** +0.05
            - **Below Average (3.0-3.4 avg):** +0.02
            - **Poor (<3.0 avg):** -0.05
            - **USE interviewEvidence.categoryScores** for detailed skill assessment

            ### 6. INTERVIEW FREQUENCY CONCERNS (Penalty)
            - **Excessive interviews indicate potential issues:**
              - 20+ interviews: -0.25 (major red flag)
              - 15-19 interviews: -0.20
              - 10-14 interviews: -0.15
              - 7-9 interviews: -0.10
              - 5-6 interviews: -0.05
              - 3-4 interviews: No penalty (healthy range)

            ## INTERVIEW EVIDENCE USAGE (CRITICAL)

            Each candidate has an `interviewEvidence` object containing:
            - **strengths:** List of proven capabilities from recent interviews (use these as primary strengths)
            - **weaknesses:** List of identified gaps from recent interviews (use these as primary concerns)
            - **categoryScores:** Average scores per technical category (e.g., {"java": 4.2, "spring": 3.8, "microservices": 3.5})
            - **recentInterviewCount:** Number of recent interviews analyzed (always 3 for eligible candidates)

            **YOU MUST prioritize interview evidence over resume claims:**
            - If interviewEvidence shows weakness in a skill, mention it as a concern even if resume claims expertise
            - If interviewEvidence shows strength in a skill, highlight it prominently
            - Use categoryScores to validate technical depth in JD-required skills

            ## ANALYSIS REQUIREMENTS

            For each candidate, provide:
            1. **Overall Match Score (0.0-1.0):** Weighted sum of all criteria
            2. **Skill Alignment Analysis:** How well their skillSet AND interviewEvidence.categoryScores match JD requirements
            3. **Experience Assessment:** YOE gap analysis and suitability
            4. **Strengths:** Top 2-3 compelling reasons from interviewEvidence.strengths and profile
            5. **Concerns:** Top 2-3 potential risks from interviewEvidence.weaknesses and gaps
            6. **Recommendation:** HIGHLY_RECOMMENDED / RECOMMENDED / CONSIDER / NOT_SUITABLE

            ## SPECIAL CONSIDERATIONS

            - **Evidence-Based Matching:** Prioritize actual interview performance over resume claims
            - **Skill Evolution:** Consider if candidate's categoryScores show growth potential
            - **Cultural Fit Indicators:** Look for learning agility, ownership in interviewEvidence.strengths
            - **Red Flags:** High interview count without placements, rating vs performance misalignment, consistent weaknesses in JD-critical skills
            - **Market Context:** %s candidates may have different expectations than BENCH/B2B

            ## OUTPUT FORMAT

            **CRITICAL: You MUST return ONLY valid JSON. Do not include any explanatory text, comments, or markdown formatting.**

            Return a JSON object (not wrapped in markdown) with this exact structure:

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
                    "analysis": "Perfect Java/Spring Boot match. Interview evidence shows strong Java (4.2/5) and Spring (3.8/5) performance."
                  },
                  "experienceAlignment": {
                    "score": 0.20,
                    "requiredYoe": 5.0,
                    "candidateYoe": 5.5,
                    "analysis": "Experience level perfectly matches requirements"
                  },
                  "strengths": [
                    "Strong Java fundamentals demonstrated in recent interviews (avg 4.2/5)",
                    "Proven Spring Boot REST API development from interview evidence",
                    "ASSET rating with only 3 interviews indicates high quality"
                  ],
                  "concerns": [
                    "Interview evidence shows microservices score of 3.2/5 - below JD requirement",
                    "Weakness noted in distributed systems from recent interviews"
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

            Return ONLY the JSON object above. No explanations, no markdown, no additional text.
            """.formatted(
                request.getClientName(),
                request.getJdTitle(), 
                request.getJdDescription(),
                request.getSource(),
                request.getSource()
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
}