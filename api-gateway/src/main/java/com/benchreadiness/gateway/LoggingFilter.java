package com.benchreadiness.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod().toString();
        String path = exchange.getRequest().getURI().getPath();
        String targetUri = exchange.getAttribute("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayRequestUrl");
        
        log.info(">>> INCOMING REQUEST: {} {} from {}", method, path, exchange.getRequest().getRemoteAddress());
        log.info(">>> TARGET URI: {}", targetUri);
        log.debug(">>> HEADERS: {}", LogRedaction.redactHeaders(exchange.getRequest().getHeaders()));
        
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            log.info("<<< RESPONSE STATUS: {} for {} {}", 
                exchange.getResponse().getStatusCode(), method, path);
        }));
    }

    @Override
    public int getOrder() {
        return -2; // Run before JWT filter
    }
}
