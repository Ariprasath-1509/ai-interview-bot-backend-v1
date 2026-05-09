package com.benchreadiness.interview.service;

import com.benchreadiness.interview.dto.CandidateMatch;
import com.benchreadiness.interview.dto.SkillBasedMatchingResult;
import com.benchreadiness.interview.entity.Client;
import com.benchreadiness.interview.entity.PositionRequirement;
import com.benchreadiness.interview.entity.SkillRequirement;
import com.benchreadiness.interview.repository.ClientRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SkillBasedMatchingService {
    
    private final ClientRepository clientRepository;
    private final MatchingService matchingService;
    
    public SkillBasedMatchingService(ClientRepository clientRepository, MatchingService matchingService) {
        this.clientRepository = clientRepository;
        this.matchingService = matchingService;
    }
    
    public List<SkillBasedMatchingResult> getSkillMatchingOverview(String userId, String userRole) {
        List<Client> clients = clientRepository.findAll().stream()
            .filter(client -> !client.getSkillRequirements().isEmpty())
            .collect(Collectors.toList());
        
        List<SkillBasedMatchingResult> results = new ArrayList<>();
        
        for (Client client : clients) {
            // Check both BENCH_B2B and MARKET sources
            if (hasPositionsForSource(client, "BENCH_B2B")) {
                results.add(getSkillBasedMatches(client.getId().toString(), "BENCH_B2B", userId, userRole));
            }
            if (hasPositionsForSource(client, "MARKET")) {
                results.add(getSkillBasedMatches(client.getId().toString(), "MARKET", userId, userRole));
            }
        }
        
        return results;
    }
    
    @Cacheable(value = "skillBasedMatches", key = "#clientId + '-' + #source")
    public SkillBasedMatchingResult getSkillBasedMatches(String clientId, String source, String userId, String userRole) {
        return computeSkillBasedMatches(clientId, source, userId, userRole, "cached");
    }
    
    public SkillBasedMatchingResult refreshSkillBasedMatches(String clientId, String source, String userId, String userRole) {
        return computeSkillBasedMatches(clientId, source, userId, userRole, "ai-fresh");
    }
    
    private SkillBasedMatchingResult computeSkillBasedMatches(String clientId, String source, String userId, String userRole, String cacheSource) {
        Client client = clientRepository.findById(java.util.UUID.fromString(clientId))
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
        
        SkillBasedMatchingResult result = new SkillBasedMatchingResult();
        result.setClientId(clientId);
        result.setClientName(client.getClientName());
        result.setSource(source);
        result.setComputedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.setCacheSource(cacheSource);
        
        List<SkillBasedMatchingResult.SkillRequirementMatch> skillMatches = new ArrayList<>();
        
        // Summary counters
        int totalPositions = 0;
        int totalCandidatesNeeded = 0;
        int totalMatchesFound = 0;
        int fullyFilledPositions = 0;
        
        for (SkillRequirement skillReq : client.getSkillRequirements()) {
            SkillBasedMatchingResult.SkillRequirementMatch skillMatch = 
                new SkillBasedMatchingResult.SkillRequirementMatch(skillReq.getSkillSet());
            
            List<SkillBasedMatchingResult.PositionMatch> positionMatches = new ArrayList<>();
            
            for (PositionRequirement posReq : skillReq.getPositions()) {
                if (!source.equals(posReq.getSource())) {
                    continue;
                }
                
                totalPositions++;
                totalCandidatesNeeded += posReq.getCandidatesNeeded();
                
                SkillBasedMatchingResult.PositionMatch posMatch = new SkillBasedMatchingResult.PositionMatch();
                posMatch.setMinYoeRequired(posReq.getMinYoeRequired());
                posMatch.setCandidatesNeeded(posReq.getCandidatesNeeded());
                posMatch.setSource(posReq.getSource());
                
                try {
                    // Get matches for this specific position requirement
                    List<CandidateMatch> matches = matchingService.findMatchingCandidates(
                        clientId, source, posReq.getCandidatesNeeded(), 
                        skillReq.getSkillSet().name(), posReq.getMinYoeRequired(), userId, userRole);
                    
                    posMatch.setMatches(matches);
                    posMatch.setMatchesFound(matches.size());
                    posMatch.setFullyFilled(matches.size() >= posReq.getCandidatesNeeded());
                    
                    totalMatchesFound += matches.size();
                    if (posMatch.getFullyFilled()) {
                        fullyFilledPositions++;
                    }
                    
                } catch (Exception e) {
                    System.err.println("Failed to get matches for position requirement: " + e.getMessage());
                    posMatch.setMatches(new ArrayList<>());
                    posMatch.setMatchesFound(0);
                    posMatch.setFullyFilled(false);
                }
                
                positionMatches.add(posMatch);
            }
            
            if (!positionMatches.isEmpty()) {
                skillMatch.setPositions(positionMatches);
                skillMatches.add(skillMatch);
            }
        }
        
        result.setSkillRequirements(skillMatches);
        
        // Create summary
        SkillBasedMatchingResult.MatchingSummary summary = new SkillBasedMatchingResult.MatchingSummary();
        summary.setTotalPositions(totalPositions);
        summary.setTotalCandidatesNeeded(totalCandidatesNeeded);
        summary.setTotalMatchesFound(totalMatchesFound);
        summary.setFullyFilledPositions(fullyFilledPositions);
        summary.setOverallFillRate(totalCandidatesNeeded > 0 ? 
            (double) totalMatchesFound / totalCandidatesNeeded * 100 : 0.0);
        
        result.setSummary(summary);
        
        return result;
    }
    
    private boolean hasPositionsForSource(Client client, String source) {
        return client.getSkillRequirements().stream()
            .flatMap(skillReq -> skillReq.getPositions().stream())
            .anyMatch(posReq -> source.equals(posReq.getSource()) && posReq.getCandidatesNeeded() > 0);
    }
}