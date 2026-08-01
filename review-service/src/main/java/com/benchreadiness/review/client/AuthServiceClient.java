package com.benchreadiness.review.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @GetMapping("/auth/my-org/features")
    Map<String, Object> getMyOrgFeatures();
}
