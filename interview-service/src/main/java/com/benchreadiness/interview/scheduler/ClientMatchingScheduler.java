package com.benchreadiness.interview.scheduler;

import com.benchreadiness.interview.entity.Client;
import com.benchreadiness.interview.repository.ClientRepository;
import com.benchreadiness.interview.service.ClientMatchingDashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClientMatchingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClientMatchingScheduler.class);

    private final ClientRepository clientRepository;
    private final ClientMatchingDashboardService dashboardService;

    public ClientMatchingScheduler(ClientRepository clientRepository,
                                  ClientMatchingDashboardService dashboardService) {
        this.clientRepository = clientRepository;
        this.dashboardService = dashboardService;
    }

    /**
     * Pre-compute matches for all active clients daily at 2 AM
     * Cron: 0 0 2 * * * (second, minute, hour, day, month, weekday)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void preComputeClientMatches() {
        log.info(">>> Starting scheduled client matching job at 2 AM");
        
        try {
            List<Client> activeClients = clientRepository.findAll().stream()
                .filter(client -> client.getStatus() == Client.ClientStatus.ACTIVE)
                .toList();

            log.info("Found {} active clients to process", activeClients.size());

            int successCount = 0;
            int failureCount = 0;

            for (Client client : activeClients) {
                try {
                    String clientId = client.getId().toString();
                    
                    // Compute BENCH/B2B matches if needed
                    if (client.getBenchB2bCandidatesNeeded() > 0) {
                        log.info("Computing BENCH/B2B matches for client: {}", client.getClientName());
                        dashboardService.refreshClientMatches(clientId, "BENCH_B2B", "system", "SUPER_ADMIN");
                        successCount++;
                    }

                    // Compute MARKET matches if needed
                    if (client.getMarketCandidatesNeeded() > 0) {
                        log.info("Computing MARKET matches for client: {}", client.getClientName());
                        dashboardService.refreshClientMatches(clientId, "MARKET", "system", "SUPER_ADMIN");
                        successCount++;
                    }

                    // Add delay to avoid overwhelming AI service
                    Thread.sleep(2000); // 2 second delay between clients

                } catch (Exception e) {
                    log.error("Failed to compute matches for client: {}", client.getClientName(), e);
                    failureCount++;
                }
            }

            log.info("<<< Scheduled client matching job completed. Success: {}, Failures: {}", 
                    successCount, failureCount);

        } catch (Exception e) {
            log.error("<<< Scheduled client matching job failed", e);
        }
    }

    /**
     * Clear stale caches every 12 hours
     * Cron: 0 0 0/12 * * * (every 12 hours)
     */
    @Scheduled(cron = "0 0 0/12 * * *")
    public void clearStaleCaches() {
        log.info(">>> Clearing stale client matching caches");
        dashboardService.clearAllCaches();
        log.info("<<< Stale caches cleared");
    }
}
