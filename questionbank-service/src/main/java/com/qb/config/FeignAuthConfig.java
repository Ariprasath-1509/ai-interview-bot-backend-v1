package com.qb.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Feign client configuration — forwards gateway auth headers to auth-service. */
public class FeignAuthConfig {

    private static void copyHeader(HttpServletRequest request, RequestTemplate template, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isBlank()) {
            template.header(name, value);
        }
    }

    @Bean
    public RequestInterceptor gatewayAuthForwardingInterceptor() {
        return (RequestTemplate template) -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return;
            }
            HttpServletRequest request = attrs.getRequest();
            copyHeader(request, template, "X-User-Id");
            copyHeader(request, template, "X-User-Role");
            copyHeader(request, template, "X-User-Email");
            copyHeader(request, template, "X-Gateway-Key");
            copyHeader(request, template, "Authorization");
        };
    }
}
