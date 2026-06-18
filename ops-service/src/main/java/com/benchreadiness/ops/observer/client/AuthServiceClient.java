package com.benchreadiness.ops.observer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
    List<Map<String, String>> getAdmins();

    @GetMapping("/auth/candidates/pipeline-status")
    Map<String, Object> getCandidatePipelineStatus();
}
