package com.benchreadiness.gateway;

import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GatewayClientConfig {

    @Bean
    public WebClient authServiceWebClient(ReactorLoadBalancerExchangeFilterFunction loadBalancerFilter) {
        return WebClient.builder()
                .filter(loadBalancerFilter)
                .build();
    }
}
