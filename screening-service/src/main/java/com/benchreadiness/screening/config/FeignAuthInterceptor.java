package com.benchreadiness.screening.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/** Forwards the caller's identity headers so downstream services see the real staff user, not screening-service itself. */
@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    private final String gatewaySharedKey;

    public FeignAuthInterceptor(@Value("${app.gateway.shared-key:}") String gatewaySharedKey) {
        this.gatewaySharedKey = gatewaySharedKey;
    }

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            String authHeader = request.getHeader("Authorization");
            if (authHeader != null) {
                template.header("Authorization", authHeader);
            }
            String userId = request.getHeader("X-User-Id");
            String userRole = request.getHeader("X-User-Role");
            String userEmail = request.getHeader("X-User-Email");
            String userOrg = request.getHeader("X-User-Org");
            if (userId != null) {
                template.header("X-User-Id", userId);
            }
            if (userRole != null) {
                template.header("X-User-Role", userRole);
            }
            if (userEmail != null) {
                template.header("X-User-Email", userEmail);
            }
            if (userOrg != null) {
                template.header("X-User-Org", userOrg);
            }
        }
        if (gatewaySharedKey != null && !gatewaySharedKey.isBlank()) {
            template.header("X-Gateway-Key", gatewaySharedKey);
        }
    }
}
