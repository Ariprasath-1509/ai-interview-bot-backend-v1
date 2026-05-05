package com.benchreadiness.interview.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Autowired
    private FeignAuthInterceptor feignAuthInterceptor;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return feignAuthInterceptor;
    }
}