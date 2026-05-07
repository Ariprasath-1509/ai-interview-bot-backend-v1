package com.benchreadiness.interview.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            // Forward Authorization header
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null) {
                template.header("Authorization", authHeader);
            }
            
            // Forward user context headers
            String userId = request.getHeader("X-User-Id");
            String userRole = request.getHeader("X-User-Role");
            String userEmail = request.getHeader("X-User-Email");
            
            if (userId != null) {
                template.header("X-User-Id", userId);
            }
            if (userRole != null) {
                template.header("X-User-Role", userRole);
            }
            if (userEmail != null) {
                template.header("X-User-Email", userEmail);
            }
        }
        
        // Forward tracing header from MDC
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            template.header("X-Trace-Id", traceId);
        }
    }
}