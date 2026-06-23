package com.benchreadiness.gateway;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class TokenIntrospectionClient {

    private final WebClient webClient;
    private final String gatewaySharedKey;

    public TokenIntrospectionClient(
            WebClient authServiceWebClient,
            @org.springframework.beans.factory.annotation.Value("${app.gateway.shared-key:}") String gatewaySharedKey) {
        this.webClient = authServiceWebClient;
        this.gatewaySharedKey = gatewaySharedKey;
    }

    public Mono<Boolean> isActive(String token) {
        return webClient.post()
                .uri("http://auth-service/auth/internal/introspect")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (gatewaySharedKey != null && !gatewaySharedKey.isBlank()) {
                        headers.set("X-Gateway-Key", gatewaySharedKey);
                    }
                })
                .bodyValue(Map.of("token", token))
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> Boolean.TRUE.equals(body.get("active")))
                .onErrorReturn(false);
    }
}
