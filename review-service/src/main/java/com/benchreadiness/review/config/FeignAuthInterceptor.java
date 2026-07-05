package com.benchreadiness.review.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Forwards the caller's identity headers onto review-service's own outgoing Feign calls
 * (e.g. to interview-service, ops-service). Without this, calls like
 * ReviewService.signOff() -> InterviewServiceClient.updateInterview() arrive with no
 * X-User-Role, so the receiving service's @PreAuthorize check rejects them with
 * AccessDeniedException — the sign-off record still saves locally, but the interview's
 * status never flips to SIGNED_OFF. Mirrors interview-service's own FeignAuthInterceptor.
 */
@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
            template.header("Authorization", authHeader);
        }
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            template.header("X-User-Id", userId);
        }
        String userRole = request.getHeader("X-User-Role");
        if (userRole != null) {
            template.header("X-User-Role", userRole);
        }
        String userEmail = request.getHeader("X-User-Email");
        if (userEmail != null) {
            template.header("X-User-Email", userEmail);
        }
        String userBranch = request.getHeader("X-User-Branch");
        if (userBranch != null) {
            template.header("X-User-Branch", userBranch);
        }
    }
}
