package com.benchreadiness.screening.client;

import com.benchreadiness.screening.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Calls auth-service's existing candidate APIs as-is — no changes made to auth-service itself. */
@FeignClient(name = "auth-service", configuration = FeignConfig.class)
public interface AuthServiceClient {

    /** POST /auth/candidates — used once, at Round 3 pass, to convert a screening candidate into a real user. */
    @PostMapping("/auth/candidates")
    Map<String, Object> createCandidate(@RequestBody Map<String, Object> request,
                                        @RequestHeader("X-User-Id") String callerId,
                                        @RequestHeader("X-User-Role") String callerRole);

    @GetMapping("/auth/master-data/{category}")
    List<Map<String, Object>> getMasterDataByCategory(@PathVariable("category") String category,
                                                      @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive);
}
