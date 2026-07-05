package com.benchreadiness.review.config;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards against the sign-off bug: without these headers forwarded, review-service's
 * calls to interview-service (e.g. ReviewService.signOff() -> updateInterview()) arrive
 * unauthenticated and get rejected by @PreAuthorize, so the interview status silently
 * never flips to SIGNED_OFF even though the sign-off record itself saves successfully.
 */
class FeignAuthInterceptorTest {

    private final FeignAuthInterceptor interceptor = new FeignAuthInterceptor();

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsIdentityHeadersFromIncomingRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer abc123");
        when(request.getHeader("X-User-Id")).thenReturn("user-1");
        when(request.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(request.getHeader("X-User-Email")).thenReturn("admin@example.com");
        when(request.getHeader("X-User-Branch")).thenReturn("DEVELOPMENT");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertTrue(template.headers().get("Authorization").contains("Bearer abc123"));
        assertTrue(template.headers().get("X-User-Id").contains("user-1"));
        assertTrue(template.headers().get("X-User-Role").contains("ADMIN"));
        assertTrue(template.headers().get("X-User-Email").contains("admin@example.com"));
        assertTrue(template.headers().get("X-User-Branch").contains("DEVELOPMENT"));
    }

    @Test
    void doesNothingWhenNoRequestContext() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        assertTrue(template.headers().isEmpty());
    }

    @Test
    void skipsHeadersThatAreAbsentOnTheIncomingRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Role")).thenReturn("SUPER_ADMIN");
        // Authorization/X-User-Id/X-User-Email/X-User-Branch left null (not stubbed)
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertTrue(template.headers().get("X-User-Role").contains("SUPER_ADMIN"));
        assertTrue(!template.headers().containsKey("Authorization"));
        assertTrue(!template.headers().containsKey("X-User-Id"));
    }
}
