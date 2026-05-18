package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AiMatchingClient;
import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.dto.CandidateMatch;
import com.benchreadiness.interview.dto.ClientMatchingOverview;
import com.benchreadiness.interview.dto.ClientMatchingResult;
import com.benchreadiness.interview.entity.Client;
import com.benchreadiness.interview.repository.ClientRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClientMatchingDashboardService {

    private final ClientRepository clientRepository;
    private final MatchingService matchingService;
    private final AuthServiceClient authServiceClient;

    public ClientMatchingDashboardService(ClientRepository clientRepository,
                                         MatchingService matchingService,
                                         AuthServiceClient authServiceClient) {
        this.clientRepository = clientRepository;
        this.matchingService = matchingService;
        this.authServiceClient = authServiceClient;
    }

    /**
     * Get all clients with matching summary (cached for 5 minutes)
     */
    @Cacheable(value = "clientOverviews", key = "'all-clients'")
    public List<ClientMatchingOverview> getAllClientsWithMatchingSummary(String userId, String userRole) {
        // Fetch all clients in a separate transaction to avoid holding DB connection
        List<Client> allClients = fetchAllClients();
        
        List<Client> activeClients = allClients.stream()
            .filter(client -> client.getStatus() == Client.ClientStatus.ACTIVE)
            .collect(Collectors.toList());
        
        // Build overviews without holding DB transaction
        List<ClientMatchingOverview> overviews = activeClients.stream()
            .map(client -> buildClientOverview(client, userId, userRole))
            .collect(Collectors.toList());
        
        return overviews;
    }
    
    @Transactional(readOnly = true)
    private List<Client> fetchAllClients() {
        return clientRepository.findAll();
    }

    /**
     * Get detailed matches for a specific client (cached per client+source)
     */
    @Cacheable(value = "clientMatches", key = "#clientId + '-' + #source")
    public ClientMatchingResult getClientMatches(String clientId, String source, 
                                                String userId, String userRole) {
        // Fetch client data in separate transaction
        Client client = fetchClientById(clientId);

        // Get matches from matching service (which calls AI) - no DB transaction here
        List<CandidateMatch> matches = matchingService.findMatchingCandidates(
            clientId, source, 50, null, null, userId, userRole
        );

        // Build summary
        Map<String, Object> summary = buildMatchingSummary(matches);

        ClientMatchingResult result = new ClientMatchingResult();
        result.setClientId(clientId);
        result.setClientName(client.getClientName());
        result.setJdRole(client.getJdRole());
        result.setJdDescription(client.getJdDescription());
        result.setSource(source);
        result.setMatches(matches);
        result.setSummary(summary);
        result.setComputedAt(LocalDateTime.now());
        result.setCacheSource("ai-fresh");

        return result;
    }
    
    @Transactional(readOnly = true)
    private Client fetchClientById(String clientId) {
        return clientRepository.findById(UUID.fromString(clientId))
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
    }

    /**
     * Refresh matches for a client (evicts cache and recomputes)
     */
    @CacheEvict(value = {"clientMatches", "clientOverviews"}, allEntries = true)
    public ClientMatchingResult refreshClientMatches(String clientId, String source,
                                                    String userId, String userRole) {
        // This will bypass cache and get fresh results
        return getClientMatches(clientId, source, userId, userRole);
    }

    /**
     * Clear all matching caches
     */
    @CacheEvict(value = {"clientMatches", "clientOverviews"}, allEntries = true)
    public void clearAllCaches() {
        System.out.println("All client matching caches cleared");
    }

    /**
     * Build client overview with matching summary
     */
    private ClientMatchingOverview buildClientOverview(Client client, String userId, String userRole) {
        ClientMatchingOverview overview = new ClientMatchingOverview();
        overview.setClientId(client.getId().toString());
        overview.setClientName(client.getClientName());
        overview.setJdRole(client.getJdRole());
        overview.setPositionsVacant(client.getPositionsVacant());
        overview.setBenchB2bCandidatesNeeded(client.getBenchB2bCandidatesNeeded());
        overview.setMarketCandidatesNeeded(client.getMarketCandidatesNeeded());
        overview.setStatus(client.getStatus().name());
        overview.setCreatedAt(client.getCreatedAt());

        // Get matching summaries from cache with timeout protection
        if (client.getBenchB2bCandidatesNeeded() > 0) {
            try {
                ClientMatchingOverview.MatchingSummary benchSummary = getMatchingSummaryFromCache(
                    client.getId().toString(), "BENCH_B2B", userId, userRole
                );
                overview.setBenchB2bSummary(benchSummary);
            } catch (Exception e) {
                System.err.println("Error getting BENCH_B2B summary for " + client.getClientName() + ": " + e.getMessage());
                overview.setBenchB2bSummary(createEmptySummary());
            }
        }

        if (client.getMarketCandidatesNeeded() > 0) {
            try {
                ClientMatchingOverview.MatchingSummary marketSummary = getMatchingSummaryFromCache(
                    client.getId().toString(), "MARKET", userId, userRole
                );
                overview.setMarketSummary(marketSummary);
            } catch (Exception e) {
                System.err.println("Error getting MARKET summary for " + client.getClientName() + ": " + e.getMessage());
                overview.setMarketSummary(createEmptySummary());
            }
        }

        return overview;
    }
    
    private ClientMatchingOverview.MatchingSummary createEmptySummary() {
        ClientMatchingOverview.MatchingSummary emptySummary = 
            new ClientMatchingOverview.MatchingSummary();
        emptySummary.setTotalMatches(0);
        emptySummary.setHighlyRecommended(0);
        emptySummary.setRecommended(0);
        emptySummary.setConsider(0);
        emptySummary.setNotSuitable(0);
        emptySummary.setLastComputedAt(LocalDateTime.now());
        emptySummary.setCached(false);
        return emptySummary;
    }

    /**
     * Get matching summary from cache (if available)
     */
    private ClientMatchingOverview.MatchingSummary getMatchingSummaryFromCache(
            String clientId, String source, String userId, String userRole) {
        
        try {
            // Get matches from in-memory cache via matching service with limited candidates
            List<CandidateMatch> matches = matchingService.findMatchingCandidates(
                clientId, source, 10, null, null, userId, userRole // Limit to 10 candidates for overview
            );

            if (matches == null || matches.isEmpty()) {
                return createEmptySummary();
            }

            Map<String, Object> summary = buildMatchingSummary(matches);

            ClientMatchingOverview.MatchingSummary matchingSummary = 
                new ClientMatchingOverview.MatchingSummary();
            matchingSummary.setTotalMatches((Integer) summary.get("totalCandidatesAnalyzed"));
            matchingSummary.setHighlyRecommended((Integer) summary.get("highlyRecommended"));
            matchingSummary.setRecommended((Integer) summary.get("recommended"));
            matchingSummary.setConsider((Integer) summary.get("consider"));
            matchingSummary.setNotSuitable((Integer) summary.get("notSuitable"));
            matchingSummary.setLastComputedAt(LocalDateTime.now());
            matchingSummary.setCached(true);

            return matchingSummary;
        } catch (Exception e) {
            System.err.println("Error getting matching summary for client " + clientId + ", source " + source + ": " + e.getMessage());
            return createEmptySummary();
        }
    }

    /**
     * Build matching summary from candidate matches
     */
    private Map<String, Object> buildMatchingSummary(List<CandidateMatch> matches) {
        Map<String, Object> summary = new HashMap<>();
        
        int highlyRecommended = 0;
        int recommended = 0;
        int consider = 0;
        int notSuitable = 0;

        for (CandidateMatch match : matches) {
            double score = match.getMatchScore();
            if (score >= 0.80) {
                highlyRecommended++;
            } else if (score >= 0.60) {
                recommended++;
            } else if (score >= 0.40) {
                consider++;
            } else {
                notSuitable++;
            }
        }

        summary.put("totalCandidatesAnalyzed", matches.size());
        summary.put("highlyRecommended", highlyRecommended);
        summary.put("recommended", recommended);
        summary.put("consider", consider);
        summary.put("notSuitable", notSuitable);
        summary.put("topMatchScore", matches.isEmpty() ? 0.0 : matches.get(0).getMatchScore());
        summary.put("averageMatchScore", matches.stream()
            .mapToDouble(CandidateMatch::getMatchScore)
            .average()
            .orElse(0.0));

        return summary;
    }
}
