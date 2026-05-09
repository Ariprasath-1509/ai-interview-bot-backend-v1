package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AiMatchingClient;
import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.client.ReviewServiceClient;
import com.benchreadiness.interview.dto.CandidateMatch;
import com.benchreadiness.interview.entity.Client;
import com.benchreadiness.interview.repository.ClientRepository;
import com.benchreadiness.interview.repository.InterviewRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MatchingService {
    
    private final ClientRepository clientRepository;
    private final AuthServiceClient authServiceClient;
    private final AiMatchingClient aiMatchingClient;
    private final InterviewRepository interviewRepository;
    private final ReviewServiceClient reviewServiceClient;
    
    public MatchingService(ClientRepository clientRepository, AuthServiceClient authServiceClient, 
                          AiMatchingClient aiMatchingClient, InterviewRepository interviewRepository,
                          ReviewServiceClient reviewServiceClient) {
        this.clientRepository = clientRepository;
        this.authServiceClient = authServiceClient;
        this.aiMatchingClient = aiMatchingClient;
        this.interviewRepository = interviewRepository;
        this.reviewServiceClient = reviewServiceClient;
    }
    
    @Cacheable(value = "candidateMatches", key = "#clientId + '-' + #source + '-' + #skillSet + '-' + #minYoeRequired + '-' + #maxCandidates")
    public List<CandidateMatch> findMatchingCandidates(String clientId, String source, Integer maxCandidates, 
                                                        String skillSet, Double minYoeRequired, String userId, String userRole) {
        try {
            // Get client details
            Client client = clientRepository.findById(UUID.fromString(clientId))
                    .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
            
            // Check if client has skill requirements
            if (!client.getSkillRequirements().isEmpty()) {
                return findMatchingCandidatesWithSkillFiltering(client, source, maxCandidates, skillSet, minYoeRequired, userId, userRole);
            }
            
            // Fallback to legacy matching for clients without skill requirements
            return findMatchingCandidatesLegacy(client, source, maxCandidates, userId, userRole);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to find matching candidates: " + e.getMessage(), e);
        }
    }
    
    private List<CandidateMatch> findMatchingCandidatesWithSkillFiltering(Client client, String source, Integer maxCandidates, 
                                                                            String filterSkillSet, Double filterMinYoe, String userId, String userRole) {
        List<CandidateMatch> allMatches = new ArrayList<>();
        
        // Get all candidates from auth service
        List<Map<String, Object>> allCandidates;
        try {
            allCandidates = authServiceClient.searchCandidates("");
            System.out.println("[MATCHING] Total candidates fetched: " + allCandidates.size());
        } catch (Exception e) {
            System.err.println("Failed to fetch candidates from auth service: " + e.getMessage());
            return createMockMatches(source, maxCandidates != null ? maxCandidates : 3);
        }
        
        // Enrich candidates with interview evidence
        allCandidates = enrichCandidatesWithInterviewEvidence(allCandidates);
        
        System.out.println("[MATCHING] Client has " + client.getSkillRequirements().size() + " skill requirements");
        
        // Process each skill requirement
        for (com.benchreadiness.interview.entity.SkillRequirement skillReq : client.getSkillRequirements()) {
            // If specific skill filter provided, skip non-matching skills
            if (filterSkillSet != null && !filterSkillSet.equals(skillReq.getSkillSet().name())) {
                System.out.println("[MATCHING] Skipping skill " + skillReq.getSkillSet() + " - doesn't match filter: " + filterSkillSet);
                continue;
            }
            
            System.out.println("[MATCHING] Processing skill: " + skillReq.getSkillSet() + " with " + skillReq.getPositions().size() + " positions");
            
            for (com.benchreadiness.interview.entity.PositionRequirement posReq : skillReq.getPositions()) {
                // If specific YOE filter provided, skip non-matching positions
                if (filterMinYoe != null && !filterMinYoe.equals(posReq.getMinYoeRequired())) {
                    System.out.println("[MATCHING] Skipping position with minYOE " + posReq.getMinYoeRequired() + " - doesn't match filter: " + filterMinYoe);
                    continue;
                }
                
                System.out.println("[MATCHING] Position requirement - Source: " + posReq.getSource() + ", MinYOE: " + posReq.getMinYoeRequired() + ", Candidates needed: " + posReq.getCandidatesNeeded());
                
                // Skip if source doesn't match
                if (!source.equals(posReq.getSource())) {
                    System.out.println("[MATCHING] Skipping position - source mismatch (requested: " + source + ", position: " + posReq.getSource() + ")");
                    continue;
                }
                
                // Filter candidates by skill set, YOE, and eligibility
                List<Map<String, Object>> filteredCandidates = allCandidates.stream()
                    .filter(candidate -> {
                        String candidateName = (String) candidate.get("name");
                        
                        // Check skill set match
                        String candidateSkill = (String) candidate.get("skillSet");
                        if (!skillReq.getSkillSet().name().equals(candidateSkill)) {
                            System.out.println("[MATCHING] " + candidateName + " filtered out - skill mismatch (has: " + candidateSkill + ", needs: " + skillReq.getSkillSet() + ")");
                            return false;
                        }
                        
                        // Check YOE requirement - use yoePortrayed for client matching
                        Double yoePortrayed = candidate.get("yoePortrayed") != null ? 
                            ((Number) candidate.get("yoePortrayed")).doubleValue() : 0.0;
                        if (yoePortrayed < posReq.getMinYoeRequired()) {
                            System.out.println("[MATCHING] " + candidateName + " filtered out - insufficient YOE portrayed (has: " + yoePortrayed + ", needs: " + posReq.getMinYoeRequired() + ")");
                            return false;
                        }
                        
                        // Check source match
                        String candidateSource = (String) candidate.get("source");
                        boolean sourceMatch = false;
                        if ("BENCH_B2B".equals(source)) {
                            sourceMatch = "BENCH".equals(candidateSource) || "B2B".equals(candidateSource);
                        } else if ("MARKET".equals(source)) {
                            sourceMatch = "MARKET".equals(candidateSource);
                        }
                        
                        if (!sourceMatch) {
                            System.out.println("[MATCHING] " + candidateName + " filtered out - source mismatch (has: " + candidateSource + ", needs: " + source + ")");
                            return false;
                        }
                        
                        // Match RFD candidates with at least 1 completed interview in our system
                        String candidateStatus = (String) candidate.get("candidateStatus");
                        Integer systemInterviewCount = candidate.get("systemInterviewCount") != null ? 
                            ((Number) candidate.get("systemInterviewCount")).intValue() : 0;
                        
                        if (!"RFD".equals(candidateStatus)) {
                            System.out.println("[MATCHING] " + candidateName + " filtered out - not RFD (status: " + candidateStatus + ")");
                            return false;
                        }
                        
                        if (systemInterviewCount < 1) {
                            System.out.println("[MATCHING] " + candidateName + " filtered out - no completed interviews (count: " + systemInterviewCount + ")");
                            return false;
                        }
                        
                        System.out.println("[MATCHING] " + candidateName + " PASSED all filters - eligible for matching");
                        return true;
                    })
                    .collect(java.util.stream.Collectors.toList());
                
                System.out.println("[MATCHING] Filtered candidates for this position: " + filteredCandidates.size());
                
                if (filteredCandidates.isEmpty()) {
                    System.out.println("[MATCHING] No candidates match the criteria for skill: " + skillReq.getSkillSet() + ", minYOE: " + posReq.getMinYoeRequired() + ", source: " + source);
                    continue;
                }
                // Use existing AI matching on filtered candidates
                try {
                    Map<String, Object> aiRequest = new HashMap<>();
                    aiRequest.put("clientId", client.getId().toString());
                    aiRequest.put("jdTitle", client.getJdRole());
                    aiRequest.put("jdDescription", client.getJdDescription());
                    aiRequest.put("clientName", client.getClientName());
                    aiRequest.put("source", source);
                    aiRequest.put("candidates", filteredCandidates);
                    aiRequest.put("maxCandidates", posReq.getCandidatesNeeded());
                    
                    Map<String, Object> aiResponse = aiMatchingClient.matchCandidates(aiRequest, userId);
                    List<CandidateMatch> positionMatches = parseAiMatchingResponse(aiResponse);
                    
                    // Add skill context to match rationale
                    for (CandidateMatch match : positionMatches) {
                        match.setMatchRationale("[" + skillReq.getSkillSet() + " - " + posReq.getMinYoeRequired() + "+ YOE] " + match.getMatchRationale());
                    }
                    
                    allMatches.addAll(positionMatches);
                    
                } catch (Exception e) {
                    System.err.println("AI matching failed for position requirement, falling back to rule-based: " + e.getMessage());
                    List<CandidateMatch> fallbackMatches = fallbackRuleBasedMatching(client, filteredCandidates, posReq.getCandidatesNeeded());
                    
                    // Add skill context to match rationale
                    for (CandidateMatch match : fallbackMatches) {
                        match.setMatchRationale("[" + skillReq.getSkillSet() + " - " + posReq.getMinYoeRequired() + "+ YOE] " + match.getMatchRationale());
                    }
                    
                    allMatches.addAll(fallbackMatches);
                }
            }
        }
        
        // Remove duplicates and sort by match score
        Map<String, CandidateMatch> uniqueMatches = new HashMap<>();
        for (CandidateMatch match : allMatches) {
            String key = match.getCandidateId();
            if (!uniqueMatches.containsKey(key) || uniqueMatches.get(key).getMatchScore() < match.getMatchScore()) {
                uniqueMatches.put(key, match);
            }
        }
        
        List<CandidateMatch> finalMatches = new ArrayList<>(uniqueMatches.values());
        finalMatches.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));
        
        int limit = maxCandidates != null ? maxCandidates : 10;
        return finalMatches.subList(0, Math.min(finalMatches.size(), limit));
    }
    
    private List<CandidateMatch> findMatchingCandidatesLegacy(Client client, String source, Integer maxCandidates, String userId, String userRole) {
        // Get candidates from auth service based on source
        List<Map<String, Object>> candidates;
        try {
            candidates = authServiceClient.searchCandidates("");
            
            // Filter by source AND eligibility criteria (RFD + 3 interviews minimum)
            candidates = candidates.stream()
                .filter(candidate -> {
                    String candidateSource = (String) candidate.get("source");
                    boolean sourceMatch = false;
                    if ("BENCH_B2B".equals(source)) {
                        sourceMatch = "BENCH".equals(candidateSource) || "B2B".equals(candidateSource);
                    } else if ("MARKET".equals(source)) {
                        sourceMatch = "MARKET".equals(candidateSource);
                    } else {
                        sourceMatch = true;
                    }
                    
                    if (!sourceMatch) return false;
                    
                    // Match RFD candidates with at least 1 completed interview in our system
                    String candidateStatus = (String) candidate.get("candidateStatus");
                    Integer systemInterviewCount = candidate.get("systemInterviewCount") != null ? 
                        ((Number) candidate.get("systemInterviewCount")).intValue() : 0;
                    
                    if (!"RFD".equals(candidateStatus)) {
                        return false;
                    }
                    
                    if (systemInterviewCount < 1) {
                        return false;
                    }
                    
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());
                
        } catch (Exception e) {
            System.err.println("Failed to fetch candidates from auth service: " + e.getMessage());
            candidates = createMockCandidates(source, maxCandidates != null ? maxCandidates : 3);
        }
        
        // Enrich candidates with interview evidence from recent 3 interviews
        candidates = enrichCandidatesWithInterviewEvidence(candidates);
        
        // Use AI-based matching
        try {
            Map<String, Object> aiRequest = new HashMap<>();
            aiRequest.put("clientId", client.getId().toString());
            aiRequest.put("jdTitle", client.getJdRole());
            aiRequest.put("jdDescription", client.getJdDescription());
            aiRequest.put("clientName", client.getClientName());
            aiRequest.put("source", source);
            aiRequest.put("candidates", candidates);
            aiRequest.put("maxCandidates", maxCandidates != null ? maxCandidates : 10);
            
            Map<String, Object> aiResponse = aiMatchingClient.matchCandidates(aiRequest, userId);
            
            return parseAiMatchingResponse(aiResponse);
            
        } catch (Exception e) {
            System.err.println("AI matching failed, falling back to rule-based: " + e.getMessage());
            return fallbackRuleBasedMatching(client, candidates, maxCandidates);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<CandidateMatch> parseAiMatchingResponse(Map<String, Object> aiResponse) {
        List<CandidateMatch> matches = new ArrayList<>();
        
        List<Map<String, Object>> aiMatches = (List<Map<String, Object>>) aiResponse.get("matches");
        if (aiMatches == null) {
            return matches;
        }
        
        for (Map<String, Object> aiMatch : aiMatches) {
            CandidateMatch match = new CandidateMatch();
            match.setCandidateId((String) aiMatch.get("candidateId"));
            match.setCandidateName((String) aiMatch.get("candidateName"));
            match.setCandidateEmail((String) aiMatch.get("candidateEmail"));
            
            // Match score from AI
            Object scoreObj = aiMatch.get("matchScore");
            match.setMatchScore(scoreObj != null ? ((Number) scoreObj).doubleValue() : 0.5);
            
            // Strengths and concerns from AI
            List<String> strengths = (List<String>) aiMatch.get("strengths");
            match.setStrengths(strengths != null ? strengths : new ArrayList<>());
            
            List<String> concerns = (List<String>) aiMatch.get("concerns");
            match.setConcerns(concerns != null ? concerns : new ArrayList<>());
            
            // Quality indicators
            Map<String, Object> qualityIndicators = (Map<String, Object>) aiMatch.get("qualityIndicators");
            if (qualityIndicators != null) {
                match.setRating((String) qualityIndicators.get("rating"));
                match.setCandidateStatus((String) qualityIndicators.get("readinessStatus"));
                Object avgScoreObj = qualityIndicators.get("avgInterviewScore");
                match.setAvgScore(avgScoreObj != null ? ((Number) avgScoreObj).doubleValue() : 0.0);
                Object interviewCountObj = qualityIndicators.get("interviewCount");
                match.setNoOfInterviews(interviewCountObj != null ? ((Number) interviewCountObj).intValue() : 0);
            }
            
            // Experience alignment - show both actual and portrayed
            Map<String, Object> expAlignment = (Map<String, Object>) aiMatch.get("experienceAlignment");
            if (expAlignment != null) {
                Object yoeActualObj = expAlignment.get("candidateYoe");
                match.setYoeActual(yoeActualObj != null ? ((Number) yoeActualObj).doubleValue() : 0.0);
                Object yoePortrayedObj = expAlignment.get("candidateYoePortrayed");
                match.setYoePortrayed(yoePortrayedObj != null ? ((Number) yoePortrayedObj).doubleValue() : 0.0);
            }
            
            // Skill alignment
            Map<String, Object> skillAlignment = (Map<String, Object>) aiMatch.get("skillAlignment");
            if (skillAlignment != null) {
                match.setSkillSet((String) skillAlignment.get("candidateSkillSet"));
            }
            
            // Build rationale from AI recommendation
            String recommendation = (String) aiMatch.get("recommendation");
            StringBuilder rationale = new StringBuilder();
            rationale.append("AI Recommendation: ").append(recommendation != null ? recommendation : "CONSIDER");
            rationale.append(" (Score: ").append(Math.round(match.getMatchScore() * 100)).append("%). ");
            
            if (skillAlignment != null && skillAlignment.get("analysis") != null) {
                rationale.append(skillAlignment.get("analysis"));
            }
            
            match.setMatchRationale(rationale.toString());
            
            matches.add(match);
        }
        
        return matches;
    }
    
    private List<CandidateMatch> fallbackRuleBasedMatching(Client client, List<Map<String, Object>> candidates, Integer maxCandidates) {
        List<CandidateMatch> matches = new ArrayList<>();
        
        for (Map<String, Object> candidate : candidates) {
            CandidateMatch match = new CandidateMatch();
            match.setCandidateId((String) candidate.get("id"));
            match.setCandidateName((String) candidate.get("name"));
            
            String email = (String) candidate.get("email");
            if (email == null || email.isBlank()) {
                email = (String) candidate.get("officialEmail");
            }
            if (email == null || email.isBlank()) {
                email = (String) candidate.get("personalEmail");
            }
            match.setCandidateEmail(email);
            
            match.setSkillSet((String) candidate.get("skillSet"));
            match.setYoeActual(candidate.get("yoeActual") != null ? 
                ((Number) candidate.get("yoeActual")).doubleValue() : 0.0);
            match.setYoePortrayed(candidate.get("yoePortrayed") != null ? 
                ((Number) candidate.get("yoePortrayed")).doubleValue() : 0.0);
            match.setRating((String) candidate.getOrDefault("rating", "MEDIUM"));
            match.setCandidateStatus((String) candidate.getOrDefault("candidateStatus", "NOT_RFD"));
            match.setNoOfInterviews(candidate.get("noOfInterviews") != null ? 
                ((Number) candidate.get("noOfInterviews")).intValue() : 0);
            match.setLastInterviewDate(candidate.get("lastInterviewDate") != null ? 
                candidate.get("lastInterviewDate").toString() : null);
            match.setLastVerdict((String) candidate.get("lastVerdict"));
            match.setAvgScore(candidate.get("avgScore") != null ? 
                ((Number) candidate.get("avgScore")).doubleValue() : 0.0);
            
            double matchScore = calculateMatchScore(client, candidate);
            match.setMatchScore(matchScore);
            
            generateMatchingInsights(match, client, candidate);
            
            matches.add(match);
        }
        
        matches.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));
        
        int limit = maxCandidates != null ? maxCandidates : 10;
        return matches.subList(0, Math.min(matches.size(), limit));
    }
    
    private double calculateMatchScore(Client client, Map<String, Object> candidate) {
        double score = 0.0; // Start from zero, earn every point
        
        // Extract candidate data
        String candidateSkill = (String) candidate.get("skillSet");
        String jdDescription = client.getJdDescription().toLowerCase();
        String jdTitle = client.getJdRole().toLowerCase();
        String combined = jdDescription + " " + jdTitle;
        Double yoeActual = candidate.get("yoeActual") != null ? 
            ((Number) candidate.get("yoeActual")).doubleValue() : 0.0;
        String rating = (String) candidate.get("rating");
        String candidateStatus = (String) candidate.get("candidateStatus");
        Double avgScore = candidate.get("avgScore") != null ? 
            ((Number) candidate.get("avgScore")).doubleValue() : 0.0;
        Integer noOfInterviews = candidate.get("noOfInterviews") != null ? 
            ((Number) candidate.get("noOfInterviews")).intValue() : 0;
        
        // 1. SKILL ALIGNMENT (25% weight) - More stringent
        double skillScore = 0.0;
        if (candidateSkill != null) {
            String skillName = candidateSkill.toLowerCase().replace("_", " ");
            
            // Exact skill matches
            if (candidateSkill.equals("JAVA_SB") && (combined.contains("java") || combined.contains("spring"))) {
                if (combined.contains("spring boot") || combined.contains("microservice")) {
                    skillScore = 0.25; // Perfect match
                } else {
                    skillScore = 0.20; // Good match
                }
            } else if (candidateSkill.equals("JFSR") && combined.contains("full stack")) {
                skillScore = 0.25;
            } else if (candidateSkill.equals("REACT_JS") && combined.contains("react")) {
                skillScore = 0.25;
            } else if (combined.contains(skillName)) {
                skillScore = 0.15; // Partial match
            } else {
                skillScore = 0.05; // Minimal transferable skills
            }
        }
        score += skillScore;
        
        // 2. EXPERIENCE ALIGNMENT (30% weight) - Much more precise
        double expScore = 0.0;
        double requiredYoe = extractRequiredExperience(combined);
        
        if (requiredYoe > 0) {
            double expGap = Math.abs(yoeActual - requiredYoe);
            if (expGap <= 0.5) {
                expScore = 0.30; // Perfect experience match
            } else if (expGap <= 1.0) {
                expScore = 0.25; // Close match
            } else if (expGap <= 2.0) {
                expScore = 0.15; // Acceptable gap
            } else if (expGap <= 3.0) {
                expScore = 0.10; // Significant gap
            } else {
                expScore = 0.05; // Major experience mismatch
            }
            
            // Penalty for under-qualification
            if (yoeActual < requiredYoe - 1.0) {
                expScore *= 0.7; // 30% penalty for being under-qualified
            }
        } else {
            // Fallback for unclear requirements
            if (yoeActual >= 2 && yoeActual <= 5) expScore = 0.20;
            else if (yoeActual > 5) expScore = 0.15;
            else expScore = 0.10;
        }
        score += expScore;
        
        // 3. RATING ASSESSMENT (15% weight) - More conservative
        double ratingScore = 0.0;
        if ("ASSET".equals(rating)) {
            ratingScore = 0.15;
        } else if ("MEDIUM".equals(rating)) {
            ratingScore = 0.08;
        } else if ("LIABILITY".equals(rating)) {
            ratingScore = -0.05; // Penalty for liability rating
        }
        score += ratingScore;
        
        // 4. READINESS STATUS (10% weight)
        double readinessScore = 0.0;
        if ("RFD".equals(candidateStatus)) {
            readinessScore = 0.10;
        } else {
            readinessScore = 0.03;
        }
        score += readinessScore;
        
        // 5. INTERVIEW PERFORMANCE (15% weight) - More nuanced
        double perfScore = 0.0;
        if (avgScore >= 4.5) {
            perfScore = 0.15;
        } else if (avgScore >= 4.0) {
            perfScore = 0.12;
        } else if (avgScore >= 3.5) {
            perfScore = 0.08;
        } else if (avgScore >= 3.0) {
            perfScore = 0.05;
        } else if (avgScore > 0) {
            perfScore = -0.05; // Penalty for poor performance
        }
        score += perfScore;
        
        // 6. INTERVIEW FREQUENCY PENALTY (5% weight) - Much more aggressive
        double frequencyPenalty = 0.0;
        if (noOfInterviews >= 20) {
            frequencyPenalty = -0.25; // Severe penalty for excessive interviews
        } else if (noOfInterviews >= 15) {
            frequencyPenalty = -0.20;
        } else if (noOfInterviews >= 10) {
            frequencyPenalty = -0.15;
        } else if (noOfInterviews >= 7) {
            frequencyPenalty = -0.10;
        } else if (noOfInterviews >= 5) {
            frequencyPenalty = -0.05;
        }
        score += frequencyPenalty;
        
        // 7. ROLE COMPLEXITY ADJUSTMENT
        double complexityAdjustment = 0.0;
        if (combined.contains("senior") || combined.contains("lead") || combined.contains("architect")) {
            if (yoeActual < 4.0) {
                complexityAdjustment = -0.15; // Significant penalty for senior roles with low experience
            } else if (yoeActual < 6.0) {
                complexityAdjustment = -0.08;
            }
        }
        if (combined.contains("principal") || combined.contains("staff")) {
            if (yoeActual < 7.0) {
                complexityAdjustment = -0.20; // Major penalty for principal roles
            }
        }
        score += complexityAdjustment;
        
        // Ensure score is between 0 and 1, but cap realistic maximum at 0.95
        return Math.min(0.95, Math.max(0.0, score));
    }
    
    private double extractRequiredExperience(String jdText) {
        // Extract years of experience from job description
        if (jdText.contains("10+ years") || jdText.contains("10 years")) return 10.0;
        if (jdText.contains("8+ years") || jdText.contains("8 years")) return 8.0;
        if (jdText.contains("7+ years") || jdText.contains("7 years")) return 7.0;
        if (jdText.contains("6+ years") || jdText.contains("6 years")) return 6.0;
        if (jdText.contains("5+ years") || jdText.contains("5 years")) return 5.0;
        if (jdText.contains("4+ years") || jdText.contains("4 years")) return 4.0;
        if (jdText.contains("3+ years") || jdText.contains("3 years")) return 3.0;
        if (jdText.contains("2+ years") || jdText.contains("2 years")) return 2.0;
        
        // Role-based experience inference
        if (jdText.contains("principal") || jdText.contains("staff")) return 8.0;
        if (jdText.contains("senior") || jdText.contains("sr.") || jdText.contains("lead")) return 5.0;
        if (jdText.contains("junior") || jdText.contains("jr.") || jdText.contains("associate")) return 2.0;
        
        return 0.0; // Unable to determine
    }
    
    private void generateMatchingInsights(CandidateMatch match, Client client, Map<String, Object> candidate) {
        List<String> strengths = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        
        // Extract candidate data
        String skillSet = (String) candidate.get("skillSet");
        Double yoeActual = candidate.get("yoeActual") != null ? 
            ((Number) candidate.get("yoeActual")).doubleValue() : 0.0;
        String rating = (String) candidate.get("rating");
        String candidateStatus = (String) candidate.get("candidateStatus");
        Double avgScore = candidate.get("avgScore") != null ? 
            ((Number) candidate.get("avgScore")).doubleValue() : 0.0;
        String lastVerdict = (String) candidate.get("lastVerdict");
        Integer noOfInterviews = candidate.get("noOfInterviews") != null ? 
            ((Number) candidate.get("noOfInterviews")).intValue() : 0;
        
        String jdLower = client.getJdDescription().toLowerCase();
        String jdTitle = client.getJdRole().toLowerCase();
        String combined = jdLower + " " + jdTitle;
        double requiredYoe = extractRequiredExperience(combined);
        
        // STRENGTHS ANALYSIS - More conservative and specific
        
        // Skill-based strengths (only if truly relevant)
        if (skillSet != null) {
            switch (skillSet) {
                case "JAVA_SB":
                    if (combined.contains("java") || combined.contains("spring")) {
                        if (yoeActual >= 3.0) {
                            strengths.add("Solid Java & Spring Boot foundation (" + String.format("%.1f", yoeActual) + " years)");
                        } else {
                            strengths.add("Basic Java & Spring Boot knowledge (" + String.format("%.1f", yoeActual) + " years)");
                        }
                        
                        if (combined.contains("microservice") && yoeActual >= 4.0) {
                            strengths.add("Microservices experience with sufficient seniority");
                        }
                    }
                    break;
                case "JFSR":
                    if (combined.contains("full stack")) {
                        strengths.add("Full-stack development capabilities");
                        if (yoeActual >= 3.0) {
                            strengths.add("Balanced frontend-backend experience");
                        }
                    }
                    break;
                case "REACT_JS":
                    if (combined.contains("react") || combined.contains("frontend")) {
                        strengths.add("Modern React.js expertise");
                        if (yoeActual >= 2.5) {
                            strengths.add("Mature frontend development skills");
                        }
                    }
                    break;
            }
        }
        
        // Experience-based strengths (realistic assessment)
        if (requiredYoe > 0) {
            double expGap = yoeActual - requiredYoe;
            if (expGap >= 0 && expGap <= 1.0) {
                strengths.add("Experience level matches role requirements (" + String.format("%.1f", yoeActual) + " vs " + String.format("%.0f", requiredYoe) + " required)");
            } else if (expGap > 1.0) {
                strengths.add("Above-required experience level (" + String.format("%.1f", yoeActual) + " vs " + String.format("%.0f", requiredYoe) + " required)");
            }
        } else if (yoeActual >= 4.0) {
            strengths.add("Experienced professional (" + String.format("%.1f", yoeActual) + " years)");
        }
        
        // Rating-based strengths (more cautious)
        if ("ASSET".equals(rating) && noOfInterviews <= 5) {
            strengths.add("Top-rated candidate with reasonable interview frequency");
        } else if ("ASSET".equals(rating)) {
            strengths.add("Top-rated candidate (but high interview frequency noted)");
        } else if ("MEDIUM".equals(rating) && noOfInterviews <= 3) {
            strengths.add("Consistent performer with limited interview exposure");
        }
        
        // Performance-based strengths (stricter thresholds)
        if (avgScore >= 4.5 && noOfInterviews <= 5) {
            strengths.add("Exceptional interview performance (avg: " + String.format("%.1f", avgScore) + ") with reasonable frequency");
        } else if (avgScore >= 4.0 && noOfInterviews <= 7) {
            strengths.add("Strong interview performance (avg: " + String.format("%.1f", avgScore) + ")");
        } else if (avgScore >= 3.5 && noOfInterviews <= 3) {
            strengths.add("Good interview performance with limited exposure");
        }
        
        // Readiness status (conditional)
        if ("RFD".equals(candidateStatus) && noOfInterviews <= 5) {
            strengths.add("Ready for deployment with reasonable interview history");
        }
        
        if ("READY".equals(lastVerdict) && noOfInterviews <= 3) {
            strengths.add("Recently assessed as deployment-ready");
        }
        
        // CONCERNS ANALYSIS - Much more comprehensive and realistic
        
        // Experience concerns (detailed analysis)
        if (requiredYoe > 0) {
            double expGap = requiredYoe - yoeActual;
            if (expGap >= 3.0) {
                concerns.add("MAJOR experience gap: " + String.format("%.1f", expGap) + " years below requirement (" + String.format("%.1f", yoeActual) + " vs " + String.format("%.0f", requiredYoe) + " required)");
            } else if (expGap >= 1.5) {
                concerns.add("Significant experience gap: " + String.format("%.1f", expGap) + " years below requirement");
            } else if (expGap >= 0.5) {
                concerns.add("Minor experience gap: " + String.format("%.1f", expGap) + " years below requirement");
            }
        }
        
        // Interview frequency concerns (much more aggressive)
        if (noOfInterviews >= 20) {
            concerns.add("CRITICAL: Excessive interview frequency (" + noOfInterviews + ") - indicates persistent rejection or selectivity issues");
        } else if (noOfInterviews >= 15) {
            concerns.add("MAJOR: Very high interview frequency (" + noOfInterviews + ") - requires investigation into rejection patterns");
        } else if (noOfInterviews >= 10) {
            concerns.add("HIGH: Frequent interviews (" + noOfInterviews + ") - may indicate fit or performance issues");
        } else if (noOfInterviews >= 7) {
            concerns.add("MODERATE: Multiple interviews (" + noOfInterviews + ") - verify availability and commitment");
        } else if (noOfInterviews >= 5) {
            concerns.add("Several interviews (" + noOfInterviews + ") - check recent feedback and availability");
        }
        
        // Performance concerns (more detailed)
        if (avgScore > 0 && avgScore < 3.0) {
            concerns.add("Poor interview performance history (avg: " + String.format("%.1f", avgScore) + ") - requires skill assessment");
        } else if (avgScore >= 3.0 && avgScore < 3.5 && noOfInterviews >= 5) {
            concerns.add("Mediocre performance with high interview frequency - may indicate consistency issues");
        }
        
        // Status and rating concerns
        if ("NOT_RFD".equals(candidateStatus)) {
            concerns.add("Currently not ready for deployment - requires preparation time and skill development");
        }
        
        if ("LIABILITY".equals(rating)) {
            concerns.add("RISK: Rated as liability - previous performance or behavioral concerns noted");
        }
        
        // Role-specific concerns (much more detailed)
        if ((combined.contains("senior") || combined.contains("sr.")) && yoeActual < 4.0) {
            concerns.add("MISMATCH: Senior role requires 4+ years, candidate has " + String.format("%.1f", yoeActual) + " years");
        }
        if ((combined.contains("lead") || combined.contains("principal")) && yoeActual < 6.0) {
            concerns.add("MISMATCH: Leadership role requires 6+ years, candidate has " + String.format("%.1f", yoeActual) + " years");
        }
        if (combined.contains("architect") && yoeActual < 7.0) {
            concerns.add("MISMATCH: Architecture role requires 7+ years, candidate has " + String.format("%.1f", yoeActual) + " years");
        }
        
        // Technology-specific concerns
        if (combined.contains("microservice") && yoeActual < 3.5) {
            concerns.add("Microservices experience typically requires 3.5+ years, candidate may lack depth");
        }
        if (combined.contains("cloud") && yoeActual < 3.0) {
            concerns.add("Cloud technologies require substantial experience, candidate may need training");
        }
        
        // Skill mismatch concerns
        if (skillSet != null && !isSkillRelevant(skillSet, combined)) {
            concerns.add("Skill set (" + skillSet + ") may not align well with role requirements");
        }
        
        // Ensure realistic assessment
        if (strengths.isEmpty()) {
            if (match.getMatchScore() >= 0.6) {
                strengths.add("Candidate meets basic role requirements");
            } else {
                strengths.add("Limited alignment with role requirements");
            }
        }
        
        if (concerns.isEmpty() && match.getMatchScore() < 0.8) {
            concerns.add("Standard evaluation recommended to assess full suitability");
        }
        
        match.setStrengths(strengths);
        match.setConcerns(concerns);
        
        // Generate realistic rationale
        StringBuilder rationale = new StringBuilder();
        int scorePercent = (int) Math.round(match.getMatchScore() * 100);
        rationale.append("Match score ").append(scorePercent).append("% ");
        
        if (match.getMatchScore() >= 0.85) {
            rationale.append("- Excellent fit. ");
        } else if (match.getMatchScore() >= 0.70) {
            rationale.append("- Good fit with minor gaps. ");
        } else if (match.getMatchScore() >= 0.50) {
            rationale.append("- Moderate fit, requires evaluation. ");
        } else if (match.getMatchScore() >= 0.30) {
            rationale.append("- Limited fit, significant concerns. ");
        } else {
            rationale.append("- Poor fit, major misalignment. ");
        }
        
        // Add key insight
        if (!strengths.isEmpty()) {
            rationale.append("Strengths: ");
            rationale.append(strengths.stream().limit(1).collect(java.util.stream.Collectors.joining(", ")));
        }
        
        if (!concerns.isEmpty()) {
            rationale.append(". Key concerns: ");
            rationale.append(concerns.stream().limit(1).collect(java.util.stream.Collectors.joining(", ")));
        }
        
        match.setMatchRationale(rationale.toString());
    }
    
    private boolean isSkillRelevant(String skillSet, String jdText) {
        switch (skillSet) {
            case "JAVA_SB":
                return jdText.contains("java") || jdText.contains("spring") || jdText.contains("backend");
            case "JFSR":
                return jdText.contains("full stack") || jdText.contains("fullstack") || 
                       (jdText.contains("java") && jdText.contains("react"));
            case "REACT_JS":
                return jdText.contains("react") || jdText.contains("frontend") || jdText.contains("javascript");
            default:
                return false;
        }
    }
    
    private List<Map<String, Object>> enrichCandidatesWithInterviewEvidence(List<Map<String, Object>> candidates) {
        for (Map<String, Object> candidate : candidates) {
            try {
                String candidateEmail = getCandidateEmail(candidate);
                if (candidateEmail == null) continue;
                
                // Get recent interviews for this candidate (up to 3 for analysis)
                List<com.benchreadiness.interview.entity.Interview> interviews = 
                    interviewRepository.findByCandidateEmailOrderByCreatedAtDesc(candidateEmail)
                        .stream()
                        .filter(i -> i.getStatus() == com.benchreadiness.interview.entity.InterviewStatus.COMPLETED ||
                                   i.getStatus() == com.benchreadiness.interview.entity.InterviewStatus.SIGNED_OFF)
                        .limit(3)
                        .collect(java.util.stream.Collectors.toList());
                
                if (interviews.isEmpty()) continue;
                
                // Aggregate pros and cons from recent interviews
                List<String> allStrengths = new ArrayList<>();
                List<String> allWeaknesses = new ArrayList<>();
                Map<String, List<Double>> categoryScores = new HashMap<>();
                
                for (com.benchreadiness.interview.entity.Interview interview : interviews) {
                    try {
                        // Get scores from review service
                        List<Map<String, Object>> scores = reviewServiceClient.getScores(interview.getId());
                        
                        for (Map<String, Object> score : scores) {
                            String dimension = (String) score.get("dimension");
                            Object valueObj = score.get("value");
                            double value = valueObj != null ? ((Number) valueObj).doubleValue() : 0.0;
                            
                            categoryScores.computeIfAbsent(dimension, k -> new ArrayList<>()).add(value);
                            
                            // Extract strengths and weaknesses
                            String strengths = (String) score.get("strengths");
                            String weaknesses = (String) score.get("weaknesses");
                            
                            if (strengths != null && !strengths.isEmpty()) {
                                allStrengths.addAll(parseJsonArray(strengths));
                            }
                            if (weaknesses != null && !weaknesses.isEmpty()) {
                                allWeaknesses.addAll(parseJsonArray(weaknesses));
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to fetch scores for interview " + interview.getId() + ": " + e.getMessage());
                    }
                }
                
                // Calculate average scores per category
                Map<String, Double> avgCategoryScores = new HashMap<>();
                for (Map.Entry<String, List<Double>> entry : categoryScores.entrySet()) {
                    double avg = entry.getValue().stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                    avgCategoryScores.put(entry.getKey(), avg);
                }
                
                // Add interview evidence to candidate data
                Map<String, Object> interviewEvidence = new HashMap<>();
                interviewEvidence.put("recentInterviewCount", interviews.size());
                interviewEvidence.put("strengths", deduplicateAndLimit(allStrengths, 5));
                interviewEvidence.put("weaknesses", deduplicateAndLimit(allWeaknesses, 5));
                interviewEvidence.put("categoryScores", avgCategoryScores);
                
                candidate.put("interviewEvidence", interviewEvidence);
                
            } catch (Exception e) {
                System.err.println("Failed to enrich candidate with interview evidence: " + e.getMessage());
            }
        }
        
        return candidates;
    }
    
    private String getCandidateEmail(Map<String, Object> candidate) {
        String email = (String) candidate.get("email");
        if (email == null || email.isBlank()) {
            email = (String) candidate.get("officialEmail");
        }
        if (email == null || email.isBlank()) {
            email = (String) candidate.get("personalEmail");
        }
        return email;
    }
    
    private List<String> parseJsonArray(String jsonArray) {
        try {
            if (jsonArray.startsWith("[") && jsonArray.endsWith("]")) {
                jsonArray = jsonArray.substring(1, jsonArray.length() - 1);
            }
            return java.util.Arrays.stream(jsonArray.split(","))
                .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    private List<String> deduplicateAndLimit(List<String> items, int limit) {
        return items.stream()
            .distinct()
            .limit(limit)
            .collect(java.util.stream.Collectors.toList());
    }
    
    private List<Map<String, Object>> createMockCandidates(String source, int maxCandidates) {
        List<Map<String, Object>> mockCandidates = new ArrayList<>();
        
        for (int i = 1; i <= Math.min(maxCandidates, 3); i++) {
            Map<String, Object> candidate = new HashMap<>();
            candidate.put("id", "mock-candidate-" + i);
            candidate.put("name", "John Doe " + i);
            candidate.put("email", "john.doe" + i + "@example.com");
            candidate.put("officialEmail", "john.doe" + i + "@company.com");
            candidate.put("personalEmail", "john.doe" + i + "@gmail.com");
            candidate.put("skillSet", "JAVA_SB");
            candidate.put("yoeActual", 3.0 + i);
            candidate.put("rating", i == 1 ? "ASSET" : "MEDIUM");
            candidate.put("candidateStatus", "RFD");
            candidate.put("noOfInterviews", i);
            candidate.put("source", source.equals("BENCH_B2B") ? "BENCH" : "MARKET");
            candidate.put("avgScore", 3.5 + (i * 0.2));
            candidate.put("lastVerdict", i == 1 ? "READY" : "NEEDS_1_WEEK_PREP");
            
            mockCandidates.add(candidate);
        }
        
        return mockCandidates;
    }
    
    private List<CandidateMatch> createMockMatches(String source, int maxCandidates) {
        List<Map<String, Object>> mockCandidates = createMockCandidates(source, maxCandidates);
        List<CandidateMatch> matches = new ArrayList<>();
        
        for (Map<String, Object> candidate : mockCandidates) {
            CandidateMatch match = new CandidateMatch();
            match.setCandidateId((String) candidate.get("id"));
            match.setCandidateName((String) candidate.get("name"));
            match.setCandidateEmail((String) candidate.get("email"));
            match.setSkillSet((String) candidate.get("skillSet"));
            match.setYoeActual(((Number) candidate.get("yoeActual")).doubleValue());
            match.setRating((String) candidate.get("rating"));
            match.setCandidateStatus((String) candidate.get("candidateStatus"));
            match.setMatchScore(0.7);
            match.setMatchRationale("Mock candidate for testing");
            matches.add(match);
        }
        
        return matches;
    }
}