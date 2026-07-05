package com.benchreadiness.ops.branch;

import com.benchreadiness.ops.observer.client.AuthServiceClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Keeps {@link BranchRegistry} in sync with auth-service's BRANCH master-data category. */
@Component
public class BranchRegistrySync {

    private static final Logger log = LoggerFactory.getLogger(BranchRegistrySync.class);

    private final AuthServiceClient authServiceClient;

    public BranchRegistrySync(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @PostConstruct
    public void warmUp() {
        refresh();
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void scheduledRefresh() {
        refresh();
    }

    private void refresh() {
        try {
            List<Map<String, Object>> entries = authServiceClient.getMasterDataByCategory("BRANCH", false);
            List<String> codes = entries.stream()
                    .map(e -> e.get("code"))
                    .filter(c -> c != null && !String.valueOf(c).isBlank())
                    .map(c -> String.valueOf(c).toUpperCase())
                    .toList();
            BranchRegistry.refresh(codes);
        } catch (Exception e) {
            log.warn("Failed to refresh branch registry from auth-service, keeping previous values: {}", e.getMessage());
        }
    }
}
