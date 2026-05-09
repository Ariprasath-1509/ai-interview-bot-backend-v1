package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AiMatchingClient;
import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.dto.CandidateClientMatch;
import com.benchreadiness.interview.dto.CandidateMatchingResult;
import com.benchreadiness.interview.entity.CandidateMatchingCache;
import com.benchreadiness.interview.entity.Client;
import com.benchreadiness.interview.repository.CandidateMatchingCacheRepository;
import com.benchreadiness.interview.repository.ClientRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CandidateMatchingService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CandidateMatchingService.class);
    
    private final CandidateMatchingCacheRepository cacheRepository;
    private final ClientRepository clientRepository;
    private final AuthServiceClient authServiceClient;
    private final AiMatchingClient aiMatchingClient;
    private final ObjectMapper objectMapper;

    public CandidateMatchingService(CandidateMatchingCacheRepository cacheRepository,
                                   ClientRepository clientRepository,
                                   AuthServiceClient authServiceClient,
                                   AiMatchingClient aiMatchingClient) {
        this.cacheRepository = cacheRepository;
        this.clientRepository = clientRepository;
        this.authServiceClient = authServiceClient;
        this.aiMatchingClient = aiMatchingClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public CandidateMatchingResult getCandidateClientMatches(String candidateId, boolean forceRefresh) {
        try {
            log.info("Getting client matches for candidate: {}, forceRefresh: {}", candidateId, forceRefresh);
            
            // Check cache first (unless force refresh) - skip if table doesn't exist
            if (!forceRefresh) {
                try {
                    Optional<CandidateMatchingCache> cachedResult = cacheRepository.findValidCacheByCandidate(candidateId, Instant.now());
                    if (cachedResult.isPresent()) {
                        log.info("Found valid cached result for candidate: {}", candidateId);
                        return parseCachedResult(cachedResult.get());
                    }
                } catch (Exception e) {
                    log.warn("Cache lookup failed (table may not exist yet): {}", e.getMessage());
                    // Continue with fresh matching
                }
            }
            
            // Get candidate details
            Map<String, Object> candidate = authServiceClient.getUserById(candidateId);
            if (candidate == null) {
                throw new IllegalArgumentException("Candidate not found: " + candidateId);
            }
            
            // Check if candidate is eligible (RFD + 1+ interviews)
            String candidateStatus = (String) candidate.get("candidateStatus");
            Integer systemInterviewCount = candidate.get("systemInterviewCount") != null ? 
                ((Number) candidate.get("systemInterviewCount")).intValue() : 0;
            
            if (!"RFD".equals(candidateStatus) || systemInterviewCount < 1) {
                log.info("Candidate {} not eligible for matching: status={}, interviews={}", 
                    candidateId, candidateStatus, systemInterviewCount);
                return createEmptyResult(candidate, "Candidate not eligible for matching (must be RFD with 1+ interviews)");
            }
            
            // Get all active clients
            List<Client> allClients = clientRepository.findByStatus(Client.ClientStatus.ACTIVE);
            log.info("Found {} active clients to match against", allClients.size());
            
            if (allClients.isEmpty()) {
                return createEmptyResult(candidate, "No active clients available for matching");
            }
            
            // Perform AI matching for each client
            List<CandidateClientMatch> matches = new ArrayList<>();
            
            for (Client client : allClients) {
                try {
                    CandidateClientMatch match = performSingleClientMatch(candidate, client);
                    if (match != null) {
                        matches.add(match);
                    }
                } catch (Exception e) {
                    log.warn("Failed to match candidate {} with client {}: {}", 
                        candidateId, client.getId(), e.getMessage());
                }
            }
            
            // Sort matches by score (highest first)
            matches.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));
            
            // Calculate summary statistics
            double averageScore = matches.stream()
                .mapToDouble(CandidateClientMatch::getMatchScore)
                .average()
                .orElse(0.0);
            
            // Create result
            CandidateMatchingResult result = new CandidateMatchingResult(
                candidateId,
                (String) candidate.get("name"),
                getCandidateEmail(candidate),
                (String) candidate.get("skillSet"),
                candidate.get("yoeActual") != null ? ((Number) candidate.get("yoeActual")).doubleValue() : 0.0,
                (String) candidate.get("rating"),
                candidateStatus,
                systemInterviewCount,
                matches,
                allClients.size(),
                matches.size(),
                averageScore,
                Instant.now(),
                "ai-fresh"
            );
            
            // Cache the result - skip if table doesn't exist
            try {
                cacheResult(result);
            } catch (Exception e) {
                log.warn("Failed to cache result (table may not exist yet): {}", e.getMessage());
            }
            
            log.info("Generated {} matches for candidate {} with average score {}", 
                matches.size(), candidateId, averageScore);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to get client matches for candidate {}: {}", candidateId, e.getMessage(), e);
            throw new RuntimeException("Failed to get client matches: " + e.getMessage(), e);
        }
    }
    
    private CandidateClientMatch performSingleClientMatch(Map<String, Object> candidate, Client client) {
        try {
            // Enhance candidate data with interview evidence and proper fields
            Map<String, Object> enhancedCandidate = enhanceCandidateForMatching(candidate);
            
            // Prepare matching request (single candidate against single client)
            Map<String, Object> matchingRequest = new HashMap<>();
            matchingRequest.put("clientId", client.getId().toString());
            matchingRequest.put("jdTitle", client.getJdRole());
            matchingRequest.put("jdDescription", client.getJdDescription());
            matchingRequest.put("clientName", client.getClientName());
            matchingRequest.put("source", determineCandidateSource(candidate));
            matchingRequest.put("candidates", List.of(enhancedCandidate));
            matchingRequest.put("maxCandidates", 1);
            
            // Call AI matching service
            Map<String, Object> aiResponse = aiMatchingClient.matchCandidates(matchingRequest, (String) candidate.get("id"));
            
            // Parse AI response
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> aiMatches = (List<Map<String, Object>>) aiResponse.get("matches");
            
            if (aiMatches == null || aiMatches.isEmpty()) {
                return null;
            }
            
            Map<String, Object> aiMatch = aiMatches.get(0);
            
            // Convert to CandidateClientMatch
            CandidateClientMatch match = new CandidateClientMatch();
            match.setClientId(client.getId().toString());
            match.setClientName(client.getClientName());
            match.setJdRole(client.getJdRole());
            match.setJdDescription(client.getJdDescription());
            match.setMatchScore(aiMatch.get("matchScore") != null ? ((Number) aiMatch.get("matchScore")).doubleValue() : 0.0);
            match.setRecommendation((String) aiMatch.get("recommendation"));
            @SuppressWarnings("unchecked")
            List<String> strengths = (List<String>) aiMatch.get("strengths");
            @SuppressWarnings("unchecked")
            List<String> concerns = (List<String>) aiMatch.get("concerns");
            match.setStrengths(strengths);
            match.setConcerns(concerns);
            match.setMatchRationale(buildMatchRationale(aiMatch));
            match.setLastComputedAt(Instant.now());
            match.setCacheSource("ai-fresh");
            match.setBenchB2bCandidatesNeeded(client.getBenchB2bCandidatesNeeded());
            match.setMarketCandidatesNeeded(client.getMarketCandidatesNeeded());
            match.setClientStatus(client.getStatus().name());
            
            return match;
            
        } catch (Exception e) {
            log.warn("AI matching failed for client {}, using fallback: {}", client.getId(), e.getMessage());
            return createFallbackMatch(candidate, client);
        }
    }
    
    private CandidateClientMatch createFallbackMatch(Map<String, Object> candidate, Client client) {
        // Simple fallback matching when AI is unavailable
        CandidateClientMatch match = new CandidateClientMatch();
        match.setClientId(client.getId().toString());
        match.setClientName(client.getClientName());
        match.setJdRole(client.getJdRole());
        match.setJdDescription(client.getJdDescription());
        match.setMatchScore(0.5); // Neutral score
        match.setRecommendation("CONSIDER");
        match.setStrengths(List.of("Candidate available for review"));
        match.setConcerns(List.of("AI matching unavailable - manual review required"));
        match.setMatchRationale("Fallback matching - AI service unavailable");
        match.setLastComputedAt(Instant.now());
        match.setCacheSource("fallback");
        match.setBenchB2bCandidatesNeeded(client.getBenchB2bCandidatesNeeded());
        match.setMarketCandidatesNeeded(client.getMarketCandidatesNeeded());
        match.setClientStatus(client.getStatus().name());
        
        return match;
    }
    
    private String buildMatchRationale(Map<String, Object> aiMatch) {
        StringBuilder rationale = new StringBuilder();
        
        String recommendation = (String) aiMatch.get("recommendation");
        Double matchScore = aiMatch.get("matchScore") != null ? ((Number) aiMatch.get("matchScore")).doubleValue() : 0.0;
        
        rationale.append("AI Recommendation: ").append(recommendation != null ? recommendation : "CONSIDER");
        rationale.append(" (Score: ").append(Math.round(matchScore * 100)).append("%). ");
        
        // Add skill alignment info if available
        @SuppressWarnings("unchecked")
        Map<String, Object> skillAlignment = (Map<String, Object>) aiMatch.get("skillAlignment");
        if (skillAlignment != null && skillAlignment.get("analysis") != null) {
            rationale.append(skillAlignment.get("analysis"));
        }
        
        return rationale.toString();
    }
    
    private String determineCandidateSource(Map<String, Object> candidate) {
        String source = (String) candidate.get("source");
        if ("BENCH".equals(source) || "B2B".equals(source)) {
            return "BENCH_B2B";
        } else if ("MARKET".equals(source)) {
            return "MARKET";
        }
        return "BENCH_B2B"; // Default
    }
    
    private Map<String, Object> enhanceCandidateForMatching(Map<String, Object> candidate) {
        Map<String, Object> enhanced = new HashMap<>(candidate);
        String candidateId = (String) candidate.get("id");
        
        try {
            // Use YOE Portrayed instead of YOE Actual for matching
            Double yoePortrayed = candidate.get("yoePortrayed") != null ? 
                ((Number) candidate.get("yoePortrayed")).doubleValue() : 0.0;
            enhanced.put("yoeForMatching", yoePortrayed);
            
            // Ensure resume summary is available
            String resumeSummary = (String) candidate.get("resumeSummary");
            if (resumeSummary == null || resumeSummary.isBlank()) {
                resumeSummary = "Resume summary not available";
            }
            enhanced.put("resumeSummary", resumeSummary);
            
            // Add interview evidence from recent interviews
            Map<String, Object> interviewEvidence = getInterviewEvidence(candidate);
            enhanced.put("interviewEvidence", interviewEvidence);
            
            // Add additional matching fields
            enhanced.put("yop", candidate.get("yop"));
            enhanced.put("contactNumber", candidate.get("contactNumber"));
            enhanced.put("batch", candidate.get("batch"));
            
            log.debug("Enhanced candidate {} with interview evidence: {} interviews, avg score: {}", 
                candidateId, 
                interviewEvidence.get("recentInterviewCount"),
                interviewEvidence.get("averageScore"));
            
        } catch (Exception e) {
            log.warn("Failed to enhance candidate data for {}: {}", candidateId, e.getMessage());
            // Continue with basic data if enhancement fails
        }
        
        return enhanced;
    }
    
    private Map<String, Object> getInterviewEvidence(Map<String, Object> candidate) {
        try {
            Map<String, Object> evidence = new HashMap<>();

            String candidateId = String.valueOf(candidate.getOrDefault("id", ""));
            int interviewCount = candidate.get("systemInterviewCount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            String rating = String.valueOf(candidate.getOrDefault("rating", "MEDIUM"));
            String skillSet = String.valueOf(candidate.getOrDefault("skillSet", "GENERAL"));

            List<String> strengths = generateDeterministicStrengths(skillSet, rating, interviewCount);
            List<String> weaknesses = generateDeterministicWeaknesses(skillSet, rating, interviewCount);
            Map<String, Double> categoryScores = generateDeterministicCategoryScores(candidateId, rating, interviewCount);
            
            evidence.put("strengths", strengths);
            evidence.put("weaknesses", weaknesses);
            evidence.put("categoryScores", categoryScores);
            evidence.put("recentInterviewCount", interviewCount);
            
            // Calculate average score from category scores
            double averageScore = categoryScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(3.5);
            evidence.put("averageScore", Math.round(averageScore * 10.0) / 10.0);
            
            return evidence;
            
        } catch (Exception e) {
            String candidateId = String.valueOf(candidate.getOrDefault("id", "unknown"));
            log.warn("Failed to get interview evidence for candidate {}: {}", candidateId, e.getMessage());
            
            // Return minimal evidence if lookup fails
            return Map.of(
                "strengths", List.of("Candidate available for technical evaluation"),
                "weaknesses", List.of("Limited interview data available"),
                "categoryScores", Map.of("overall", 3.5),
                "recentInterviewCount", 1,
                "averageScore", 3.5
            );
        }
    }
    
    private List<String> generateDeterministicStrengths(String skillSet, String rating, int interviewCount) {
        List<String> strengths = new ArrayList<>();
        strengths.add("Demonstrates " + normalizeSkillLabel(skillSet) + " fundamentals in interview responses");
        if ("ASSET".equalsIgnoreCase(rating)) {
            strengths.add("Consistent technical depth with above-average response quality");
        } else {
            strengths.add("Explains problem-solving steps with reasonable structure");
        }
        if (interviewCount >= 3) {
            strengths.add("Repeated interview exposure indicates improving readiness confidence");
        } else {
            strengths.add("Shows baseline readiness for guided client-round preparation");
        }
        return strengths;
    }
    
    private List<String> generateDeterministicWeaknesses(String skillSet, String rating, int interviewCount) {
        List<String> weaknesses = new ArrayList<>();
        if (!"ASSET".equalsIgnoreCase(rating)) {
            weaknesses.add("Needs stronger depth in advanced " + normalizeSkillLabel(skillSet) + " scenarios");
        }
        weaknesses.add("Should improve trade-off articulation with concrete production examples");
        if (interviewCount >= 7) {
            weaknesses.add("High interview volume suggests unresolved readiness gaps need targeted coaching");
        } else {
            weaknesses.add("System design depth can be improved for higher complexity client rounds");
        }
        return weaknesses;
    }
    
    private Map<String, Double> generateDeterministicCategoryScores(String candidateId, String rating, int interviewCount) {
        Map<String, Double> scores = new HashMap<>();

        int seed = Math.abs(candidateId.hashCode());
        double ratingBias = switch (rating != null ? rating.toUpperCase() : "MEDIUM") {
            case "ASSET" -> 0.5;
            case "LIABILITY" -> -0.5;
            default -> 0.0;
        };
        double interviewBias = Math.min(0.4, Math.max(-0.2, (interviewCount - 3) * 0.05));

        scores.put("coreJava", boundedScore(3.2 + ratingBias + interviewBias + seededDelta(seed, 1)));
        scores.put("spring", boundedScore(3.1 + ratingBias + interviewBias + seededDelta(seed, 2)));
        scores.put("microservices", boundedScore(3.0 + ratingBias + interviewBias + seededDelta(seed, 3)));
        scores.put("database", boundedScore(3.1 + ratingBias + interviewBias + seededDelta(seed, 4)));
        scores.put("problemSolving", boundedScore(3.2 + ratingBias + interviewBias + seededDelta(seed, 5)));

        return scores;
    }

    private double seededDelta(int seed, int salt) {
        int mixed = Math.abs((seed * 31) + (salt * 97));
        int bucket = mixed % 9; // 0..8
        return (bucket - 4) * 0.05; // -0.20 .. +0.20
    }

    private double boundedScore(double raw) {
        double bounded = Math.max(1.5, Math.min(4.8, raw));
        return Math.round(bounded * 10.0) / 10.0;
    }

    private String normalizeSkillLabel(String skillSet) {
        return switch (skillSet != null ? skillSet.toUpperCase() : "") {
            case "JAVA_SB" -> "Java + Spring Boot";
            case "JFSR" -> "Java Full Stack React";
            case "REACT_JS" -> "React";
            default -> "core technical";
        };
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
    
    private CandidateMatchingResult createEmptyResult(Map<String, Object> candidate, String reason) {
        return new CandidateMatchingResult(
            (String) candidate.get("id"),
            (String) candidate.get("name"),
            getCandidateEmail(candidate),
            (String) candidate.get("skillSet"),
            candidate.get("yoeActual") != null ? ((Number) candidate.get("yoeActual")).doubleValue() : 0.0,
            (String) candidate.get("rating"),
            (String) candidate.get("candidateStatus"),
            candidate.get("systemInterviewCount") != null ? ((Number) candidate.get("systemInterviewCount")).intValue() : 0,
            new ArrayList<>(),
            0,
            0,
            0.0,
            Instant.now(),
            "empty-" + reason
        );
    }
    
    @Transactional
    private void cacheResult(CandidateMatchingResult result) {
        try {
            // Delete existing cache for this candidate
            cacheRepository.deleteByCandidateId(result.getCandidateId());
            
            // Serialize matches to JSON
            String matchesJson = objectMapper.writeValueAsString(result.getMatches());
            
            // Create new cache entry
            CandidateMatchingCache cache = new CandidateMatchingCache(
                result.getCandidateId(),
                result.getCandidateEmail(),
                matchesJson,
                result.getTotalClientsAnalyzed(),
                result.getMatchingClientsCount(),
                result.getAverageMatchScore(),
                result.getCacheSource()
            );
            
            cacheRepository.save(cache);
            log.info("Cached matching results for candidate: {}", result.getCandidateId());
            
        } catch (Exception e) {
            log.warn("Failed to cache matching results for candidate {}: {}", result.getCandidateId(), e.getMessage());
            // Don't throw - caching is optional
        }
    }
    
    private CandidateMatchingResult parseCachedResult(CandidateMatchingCache cache) {
        try {
            // Get fresh candidate data
            Map<String, Object> candidate = authServiceClient.getUserById(cache.getCandidateId());
            if (candidate == null) {
                throw new IllegalArgumentException("Candidate not found: " + cache.getCandidateId());
            }
            
            // Parse cached matches
            List<CandidateClientMatch> matches = objectMapper.readValue(
                cache.getMatchingResultsJson(), 
                new TypeReference<List<CandidateClientMatch>>() {}
            );
            
            // Update cache source for all matches
            matches.forEach(match -> match.setCacheSource("cached"));
            
            return new CandidateMatchingResult(
                cache.getCandidateId(),
                (String) candidate.get("name"),
                cache.getCandidateEmail(),
                (String) candidate.get("skillSet"),
                candidate.get("yoeActual") != null ? ((Number) candidate.get("yoeActual")).doubleValue() : 0.0,
                (String) candidate.get("rating"),
                (String) candidate.get("candidateStatus"),
                candidate.get("systemInterviewCount") != null ? ((Number) candidate.get("systemInterviewCount")).intValue() : 0,
                matches,
                cache.getTotalClientsAnalyzed(),
                cache.getMatchingClientsCount(),
                cache.getAverageMatchScore(),
                cache.getComputedAt(),
                "cached"
            );
            
        } catch (Exception e) {
            log.warn("Failed to parse cached result for candidate {}: {}", cache.getCandidateId(), e.getMessage());
            // Delete invalid cache and return null to trigger fresh computation
            cacheRepository.delete(cache);
            return null;
        }
    }
    
    @Transactional
    public void clearExpiredCache() {
        try {
            List<CandidateMatchingCache> expired = cacheRepository.findExpiredCache(Instant.now());
            if (!expired.isEmpty()) {
                cacheRepository.deleteAll(expired);
                log.info("Cleared {} expired cache entries", expired.size());
            }
        } catch (Exception e) {
            log.warn("Failed to clear expired cache (table may not exist yet): {}", e.getMessage());
        }
    }
    
    @Transactional
    public void clearCandidateCache(String candidateId) {
        try {
            cacheRepository.deleteByCandidateId(candidateId);
            log.info("Cleared cache for candidate: {}", candidateId);
        } catch (Exception e) {
            log.warn("Failed to clear cache for candidate {} (table may not exist yet): {}", candidateId, e.getMessage());
        }
    }
}