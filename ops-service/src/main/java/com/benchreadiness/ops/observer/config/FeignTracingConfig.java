package com.benchreadiness.ops.observer.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignTracingConfig {

    @Bean
    public RequestInterceptor internalServiceRequestInterceptor(
            @Value("${app.gateway.shared-key:}") String gatewaySharedKey) {
        return template -> {
            String traceId = MDC.get("traceId");
            if (traceId != null) {
                template.header("X-Trace-Id", traceId);
            }

            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                copyHeader(request, template, "X-User-Id");
                copyHeader(request, template, "X-User-Role");
                copyHeader(request, template, "X-Gateway-Key");
            }

            if (!hasHeader(template, "X-User-Role")) {
                template.header("X-User-Id", "system-ops");
                template.header("X-User-Role", "SUPER_ADMIN");
            }
            if (gatewaySharedKey != null && !gatewaySharedKey.isBlank() && !hasHeader(template, "X-Gateway-Key")) {
                template.header("X-Gateway-Key", gatewaySharedKey);
            }
        };
    }

    private static void copyHeader(HttpServletRequest request, RequestTemplate template, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isBlank()) {
            template.header(name, value);
        }
    }

    private static boolean hasHeader(RequestTemplate template, String name) {
        return template.headers().containsKey(name) && !template.headers().get(name).isEmpty();
    }
}
