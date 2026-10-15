package com.benchreadiness.ops.observer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "auth-service", contextId = "observerAuthServiceClient")
public interface AuthServiceClient {

    @GetMapping("/auth/users/{id}")
    Map<String, Object> getUser(@PathVariable("id") String id);

    @GetMapping("/auth/staff")
    List<Map<String, Object>> getAllStaff();

    @GetMapping("/auth/internal/client-notification-recipients")
    List<Map<String, Object>> getClientNotificationRecipients();

    @GetMapping("/auth/admins")
    List<Map<String, Object>> getAdmins();

    /** Org-scoped by the caller headers the Feign interceptor copies. */
    @GetMapping("/auth/candidates/pipeline-status")
    Map<String, Object> getCandidatePipelineStatusForCaller();

    @GetMapping("/auth/master-data/{category}")
    List<Map<String, Object>> getMasterDataByCategory(@PathVariable("category") String category,
                                                      @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive);

    @GetMapping("/auth/my-org/features")
    Map<String, Object> getMyOrgFeatures();
}
