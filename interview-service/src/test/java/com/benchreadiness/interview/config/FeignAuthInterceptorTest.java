package com.benchreadiness.interview.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fix 6.10 root cause: user headers are only forwarded from the servlet request thread.
 * Bulk interview creation runs on bulkInterviewExecutor, so calls such as
 * POST /ai/generate-rubric go out without X-User-Id (ai-service now defaults it to "system").
 */
class FeignAuthInterceptorTest {

    private final FeignAuthInterceptor interceptor = new FeignAuthInterceptor();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsUserIdOnRequestThread() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "staff-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("X-User-Id")).containsExactly("staff-1");
    }

    @Test
    void sendsNoUserIdFromWorkerThread() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "staff-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var pool = Executors.newSingleThreadExecutor();
        try {
            RequestTemplate template = CompletableFuture.supplyAsync(() -> {
                RequestTemplate t = new RequestTemplate();
                interceptor.apply(t);
                return t;
            }, pool).get();

            assertThat(template.headers()).doesNotContainKey("X-User-Id");
        } finally {
            pool.shutdown();
        }
    }
}
