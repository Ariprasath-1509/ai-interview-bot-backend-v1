package com.benchreadiness.review.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "compliance-service")
public interface ComplianceServiceClient {

    @PostMapping("/compliance/audit-logs")
    void recordAuditLog(@RequestBody Map<String, Object> auditLog);
}
