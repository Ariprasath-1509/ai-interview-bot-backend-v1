package com.benchreadiness.ai.controller;

import com.benchreadiness.ai.dto.RubricRequest;
import com.benchreadiness.ai.service.RubricService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fix 6.10: POST /ai/generate-rubric must not fail with 400 when X-User-Id is absent
 * (interview-service bulk create sends no user headers), and must still pass the caller's id when present.
 */
class GenerateRubricHeaderTest {

    private static final String BODY = "{\"jdTitle\":\"Java Backend Developer\",\"jdText\":\"Spring Boot\"}";

    private final RubricService rubricService = mock(RubricService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
        new AiController(null, null, null, rubricService, null, null,
            null, null, null, null, null, new ObjectMapper())).build();

    @Test
    void withHeaderPassesCallerIdUnchanged() throws Exception {
        when(rubricService.generateRubric(any(), eq("user-42")))
            .thenReturn(Map.of("rubric", Map.of("categories", java.util.List.of())));

        mvc.perform(post("/ai/generate-rubric").header("X-User-Id", "user-42")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rubric.categories").isArray());

        verify(rubricService).generateRubric(
            argThat((RubricRequest r) -> "Java Backend Developer".equals(r.getJdTitle())), eq("user-42"));
    }

    @Test
    void withoutHeaderSucceedsAsSystem() throws Exception {
        when(rubricService.generateRubric(any(), eq("system")))
            .thenReturn(Map.of("rubric", Map.of("categories", java.util.List.of())));

        // Before 6.10 this returned 400 "Required request header 'X-User-Id' ... is not present".
        mvc.perform(post("/ai/generate-rubric")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rubric.categories").isArray());

        verify(rubricService).generateRubric(any(), eq("system"));
    }
}
