package com.benchreadiness.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String TYPE_ACCESS = "access";

    private static final List<String> PUBLIC_PATHS = List.of(
        "/auth/login",
        "/auth/logout",
        "/auth/refresh",
        "/auth/register",
        "/auth/forgot-password",
        "/auth/reset-password",
        "/auth/internal/introspect",
        "/auth/master-data",
        "/api/qb",
        "/api/questionbank",
        "/questionbank",
        "/api/screening/public",
        "/screening/public"
    );

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final String gatewaySharedKey;
    private final TokenIntrospectionClient introspectionClient;

    public JwtAuthFilter(
            TokenIntrospectionClient introspectionClient,
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.issuer:benchreadiness-auth}") String issuer,
            @Value("${app.jwt.audience:benchreadiness-api}") String audience,
            @Value("${app.gateway.shared-key:}") String gatewaySharedKey) {
        this.introspectionClient = introspectionClient;
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.gatewaySharedKey = gatewaySharedKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isActuatorHealth(path) || PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JWT Filter: Missing or invalid Authorization header for path {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("JWT Filter: Token validation failed for path {}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (!TYPE_ACCESS.equals(claims.get("typ", String.class))) {
            log.warn("JWT Filter: Non-access token rejected for path {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);
        String email = claims.get("email", String.class);
        String branch = claims.get("branch", String.class);
        String org = claims.get("org", String.class);

        return introspectionClient.isActive(token).flatMap(active -> {
            if (!active) {
                log.warn("JWT Filter: Revoked or inactive token for userId {}", userId);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(h -> {
                        h.set("X-User-Id", userId);
                        h.set("X-User-Role", role);
                        if (email != null) {
                            h.set("X-User-Email", email);
                        }
                        if (branch != null && !branch.isBlank()) {
                            h.set("X-User-Branch", branch);
                        }
                        if (org != null && !org.isBlank()) {
                            h.set("X-User-Org", org);
                        }
                        if (gatewaySharedKey != null && !gatewaySharedKey.isBlank()) {
                            h.set("X-Gateway-Key", gatewaySharedKey);
                        }
                    }))
                    .build();

            return chain.filter(mutated);
        });
    }

    private boolean isActuatorHealth(String path) {
        return "/actuator/health".equals(path) || path.startsWith("/actuator/health/");
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
