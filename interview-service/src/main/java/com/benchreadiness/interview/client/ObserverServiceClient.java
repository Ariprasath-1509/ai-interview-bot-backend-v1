package com.benchreadiness.interview.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "observer-service-client", url = "http://localhost:6002")
public interface ObserverServiceClient {

    @PostMapping("/observer/notify/interview-created")
    void notifyInterviewCreated(@RequestBody Map<String, String> request);

    @PostMapping("/observer/notify/client-created")
    void notifyClientCreated(@RequestBody Map<String, Object> request);

    @PostMapping("/observer/notify/interview-abandoned")
    void notifyInterviewAbandoned(@RequestBody Map<String, String> request);

    @PostMapping("/observer/notify/interview-cancelled")
    void notifyInterviewCancelled(@RequestBody Map<String, String> request);

    @PostMapping("/observer/inject")
    void injectQuestion(@RequestBody Map<String, Object> request,
                       @RequestHeader("X-User-Id") String userId);

    @PostMapping("/observer/flag")
    void flagAnswer(@RequestBody Map<String, Object> request,
                   @RequestHeader("X-User-Id") String userId);
}