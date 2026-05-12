package com.qb.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "auth-service", path = "/auth")
public interface AuthServiceClient {

    @GetMapping("/me")
    Map<String, Object> getCurrentUser(@RequestHeader("Authorization") String token);

    @GetMapping("/users/{userId}")
    Map<String, Object> getUserById(@PathVariable("userId") String userId);

    @GetMapping("/staff")
    List<Map<String, Object>> getStaff();

    @GetMapping("/admins")
    List<Map<String, Object>> getAdmins();

    @GetMapping("/candidates")
    List<Map<String, Object>> getCandidates(@RequestParam(value = "search", required = false) String search);

    @GetMapping("/candidates/{candidateId}")
    Map<String, Object> getCandidateById(@PathVariable("candidateId") String candidateId);

    @PostMapping("/candidates/{candidateId}/increment-interview-count")
    Map<String, Object> incrementInterviewCount(@PathVariable("candidateId") String candidateId);

    @PostMapping("/candidates/by-email/{email}/increment-interview-count")
    Map<String, Object> incrementInterviewCountByEmail(@PathVariable("email") String email);
}