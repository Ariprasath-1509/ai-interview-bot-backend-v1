package com.benchreadiness.interview.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "questionbank-service", path = "/api/questions")
public interface QuestionBankClient {

    /**
     * Fetch questions by company and interview mode.
     * Example: GET /api/questions/interview-mode?company=rebit&mode=L1
     */
    @GetMapping("/interview-mode")
    JsonNode fetchQuestionsByCompanyAndMode(
            @RequestParam("company") String company,
            @RequestParam("mode") String mode
    );
}
