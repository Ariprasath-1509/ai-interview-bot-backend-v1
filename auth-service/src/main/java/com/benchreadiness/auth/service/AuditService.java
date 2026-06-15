package com.benchreadiness.auth.service;

import com.benchreadiness.auth.client.ComplianceServiceClient;
import com.benchreadiness.auth.util.PiiRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final ComplianceServiceClient complianceServiceClient;

    public AuditService(ComplianceServiceClient complianceServiceClient) {
        this.complianceServiceClient = complianceServiceClient;
    }

    public void record(String actorId, String actorName, String actorRole, String action,
                       String resource, String resourceId, String detail) {
        record(actorId, actorName, actorRole, action, resource, resourceId, detail, null, null);
    }

    public void record(String actorId, String actorName, String actorRole, String action,
                       String resource, String resourceId, String detail,
                       String oldValue, String newValue) {
        try {
            Map<String, Object> auditLog = new HashMap<>();
            auditLog.put("actorId", actorId);
            if (actorName != null) {
                auditLog.put("actorName", actorName);
            }
            auditLog.put("actorRole", actorRole);
            auditLog.put("action", action);
            auditLog.put("resource", resource);
            auditLog.put("resourceId", resourceId);
            if (detail != null) {
                auditLog.put("detail", detail);
            }
            if (oldValue != null) {
                auditLog.put("oldValue", oldValue);
            }
            if (newValue != null) {
                auditLog.put("newValue", newValue);
            }
            auditLog.put("ipAddress", resolveClientIp());
            complianceServiceClient.recordAuditLog(auditLog);
            log.info("audit action={} resource={} actorId={}", action, resource, actorId);
        } catch (Exception e) {
            log.error("Failed to record audit log for action {}: {}", action, e.getMessage());
        }
    }

    public void recordLoginSuccess(String userId, String role, String email) {
        record(userId, null, role, "LOGIN_SUCCESS", "AUTH", userId,
                "Login succeeded for " + PiiRedactor.maskEmail(email));
    }

    public void recordLoginFailure(String email) {
        record("anonymous", null, "ANONYMOUS", "LOGIN_FAILURE", "AUTH", "unknown",
                "Login failed for " + PiiRedactor.maskEmail(email));
    }

    public void recordLogout(String userId, String role) {
        record(userId, null, role, "LOGOUT", "AUTH", userId, "User logged out");
    }

    public void recordTokenRefresh(String userId, String role) {
        record(userId, null, role, "TOKEN_REFRESH", "AUTH", userId, "Access token refreshed");
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "internal";
            }
            var request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                return ip.split(",")[0].trim();
            }
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isBlank()) {
                return ip.trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
