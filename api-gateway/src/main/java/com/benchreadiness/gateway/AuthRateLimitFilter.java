package com.benchreadiness.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimitFilter implements GlobalFilter, Ordered {

    private static final List<String> RATE_LIMITED_PATHS = List.of(
            "/auth/login",
            "/auth/register",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/refresh"
    );

    private final int maxRequests;
    private final long windowSeconds;
    private final ConcurrentHashMap<String, RequestWindow> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            @Value("${app.auth-rate-limit.max-requests:20}") int maxRequests,
            @Value("${app.auth-rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (RATE_LIMITED_PATHS.stream().noneMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String clientKey = resolveClientKey(exchange);
        Instant now = Instant.now();
        RequestWindow window = windows.compute(clientKey, (key, existing) -> {
            if (existing == null || existing.windowStart.plusSeconds(windowSeconds).isBefore(now)) {
                return new RequestWindow(now, 1);
            }
            existing.count++;
            return existing;
        });

        if (window.count > maxRequests) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private String resolveClientKey(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null
                && exchange.getRequest().getRemoteAddress().getAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return -2;
    }

    private static class RequestWindow {
        private final Instant windowStart;
        private int count;

        private RequestWindow(Instant windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
