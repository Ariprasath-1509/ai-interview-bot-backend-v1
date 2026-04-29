package com.benchreadiness.interview.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @GetMapping("/auth/users/{userId}")
    Map<String, Object> getUserById(@PathVariable("userId") String userId);

    @GetMapping("/auth/candidates")
    Map<String, Object> searchCandidates(@RequestParam("search") String search);

    @PostMapping("/auth/register")
    Map<String, Object> registerCandidate(@RequestBody Map<String, String> request);

    @PostMapping("/auth/login")
    Map<String, Object> login(@RequestBody Map<String, String> request);
}