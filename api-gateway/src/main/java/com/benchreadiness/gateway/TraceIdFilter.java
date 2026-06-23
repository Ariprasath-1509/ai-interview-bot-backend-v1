package com.benchreadiness.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        
        MDC.put("traceId", traceId);
        log.debug("TraceId assigned: {}", traceId);
        
        final String finalTraceId = traceId;
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(h -> h.set(TRACE_ID_HEADER, finalTraceId)))
                .build();
        
        return chain.filter(mutated).doFinally(signalType -> MDC.clear());
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
