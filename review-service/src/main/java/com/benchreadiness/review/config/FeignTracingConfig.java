package com.benchreadiness.review.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignTracingConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                String traceId = MDC.get("traceId");
                if (traceId != null) {
                    template.header("X-Trace-Id", traceId);
                }
            }
        };
    }
}
