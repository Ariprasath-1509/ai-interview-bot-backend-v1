package com.benchreadiness.ops.observer.config;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;

public class FeignTracingConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            String traceId = MDC.get("traceId");
            if (traceId != null) template.header("X-Trace-Id", traceId);
        };
    }
}
