package com.qb.client;

import com.qb.config.FeignAuthConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "auth-service", path = "/auth", configuration = FeignAuthConfig.class)
public interface AuthServiceClient {

    @GetMapping("/me")
    Map<String, Object> getCurrentUser(@RequestHeader("Authorization") String token);

    @GetMapping("/users/{userId}")
    Map<String, Object> getUserById(@PathVariable("userId") String userId);

    @GetMapping("/internal/candidates")
    List<Map<String, Object>> getCandidatesInternal();

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

    @GetMapping("/master-data")
    Map<String, List<Map<String, Object>>> getAllMasterData(
            @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive);

    @GetMapping("/master-data/categories")
    List<String> getMasterDataCategories();

    @GetMapping("/master-data/{category}")
    List<Map<String, Object>> getMasterDataByCategory(
            @PathVariable("category") String category,
            @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive);

    @PostMapping("/master-data/{category}")
    Map<String, Object> createMasterDataEntry(
            @PathVariable("category") String category,
            @RequestBody Map<String, Object> body);

    @PutMapping("/master-data/{category}/{id}")
    Map<String, Object> updateMasterDataEntry(
            @PathVariable("category") String category,
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body);

    @DeleteMapping("/master-data/{category}/{id}")
    Map<String, Object> deactivateMasterDataEntry(
            @PathVariable("category") String category,
            @PathVariable("id") UUID id);
}
