package com.benchreadiness.interview.client;

import com.benchreadiness.interview.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "auth-service", configuration = FeignConfig.class)
public interface AuthServiceClient {

    @GetMapping("/auth/users/{userId}")
    Map<String, Object> getUserById(@PathVariable("userId") String userId);

    @GetMapping("/auth/users/by-email/{email}")
    Map<String, Object> getUserByEmail(@PathVariable("email") String email);

    @GetMapping("/auth/candidates")
    List<Map<String, Object>> searchCandidates(@RequestParam("search") String search);

    @GetMapping("/auth/candidates")
    List<Map<String, Object>> searchCandidates(@RequestParam Map<String, Object> params);

    @PostMapping("/auth/register")
    Map<String, Object> registerCandidate(@RequestBody Map<String, String> request);

    @PostMapping("/auth/login")
    Map<String, Object> login(@RequestBody Map<String, String> request);

    @PostMapping("/auth/candidates/{candidateId}/resume")
    Map<String, Object> updateCandidateResume(@PathVariable("candidateId") String candidateId,
                                             @RequestBody Map<String, Object> resumeData);

    @PostMapping("/auth/candidates/{candidateId}/increment-interview-count")
    Map<String, Object> incrementSystemInterviewCount(@PathVariable("candidateId") String candidateId);

    @PostMapping("/auth/candidates/by-email/{email}/increment-interview-count")
    Map<String, Object> incrementSystemInterviewCountByEmail(@PathVariable("email") String email);

    @PostMapping("/auth/candidates/by-email/{email}/set-system-interview-count")
    Map<String, Object> setSystemInterviewCountByEmail(@PathVariable("email") String email, @RequestBody Map<String, Integer> request);

    @PostMapping("/auth/candidates/recalculate-system-interview-counts")
    Map<String, Object> recalculateSystemInterviewCounts();

    @GetMapping("/auth/candidates/{candidateId}")
    Map<String, Object> getCandidateById(@PathVariable("candidateId") String candidateId,
                                        @RequestHeader("X-User-Id") String userId);

    @GetMapping("/auth/proctoring-settings")
    Map<String, Object> getProctoringSettings();
}