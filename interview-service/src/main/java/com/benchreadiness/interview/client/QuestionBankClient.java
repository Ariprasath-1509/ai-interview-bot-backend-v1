package com.benchreadiness.interview.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "questionbank-service", path = "/api/questions")
public interface QuestionBankClient {

    @GetMapping("/interview-mode")
    JsonNode fetchQuestionsByCompanyAndMode(
            @RequestParam("company") String company,
            @RequestParam("mode") String mode
    );

    /**
     * Fetch questions for interview creation picker.
     * GET /api/questions/for-interview?search=&category=&size=100
     */
    @GetMapping("/for-interview")
    JsonNode fetchQuestionsForInterview(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "100") int size
    );
}
