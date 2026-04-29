package com.benchreadiness.observer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {
    
    @GetMapping("/auth/users/{id}")
    Map<String, Object> getUser(@PathVariable("id") String id);
}