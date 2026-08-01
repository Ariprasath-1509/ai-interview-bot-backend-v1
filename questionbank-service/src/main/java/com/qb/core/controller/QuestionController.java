package com.qb.core.controller;

import com.qb.core.dto.ApiResponse;
import com.qb.core.dto.QuestionDTO;
import com.qb.core.dto.QuestionUpdateRequest;
import com.qb.core.service.QuestionService;
import com.qb.feature.FeatureKey;
import com.qb.feature.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    /**
     * GET /api/questions?search=kafka&company=mantra-labs&round=l1&category=Kafka&tag=event-driven&page=0&size=20
     */
    @GetMapping
    @RequiresFeature(FeatureKey.QUESTION_BANK)
    public ResponseEntity<ApiResponse<Page<QuestionDTO>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String interviewType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String round,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String importance,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<QuestionDTO> result = questionService.searchQuestions(
                search, interviewType, category, company, round, tag, importance, page, size
        );
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * GET /api/questions/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionDTO>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(questionService.getById(id)));
    }

    /**
     * PUT /api/questions/{id} — Admin only
     */
    @PutMapping("/{id}")
    @RequiresFeature(FeatureKey.QUESTION_BANK)
    public ResponseEntity<ApiResponse<QuestionDTO>> update(
            @PathVariable UUID id,
            @Valid @RequestBody QuestionUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(questionService.updateQuestion(id, request)));
    }

    /**
     * DELETE /api/questions/{id} — Admin only
     */
    @DeleteMapping("/{id}")
    @RequiresFeature(FeatureKey.QUESTION_BANK)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        questionService.deleteQuestion(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Question deleted"));
    }

    /**
     * GET /api/questions/interview-mode?company=rebit&mode=L1
     * Fetches ALL questions for a specific company and interview mode
     */
    @GetMapping("/interview-mode")
    public ResponseEntity<ApiResponse<List<QuestionDTO>>> getByCompanyAndMode(
            @RequestParam String company,
            @RequestParam String mode
    ) {
        List<QuestionDTO> questions = questionService.getQuestionsByCompanyAndMode(company, mode);
        return ResponseEntity.ok(ApiResponse.ok(questions));
    }

    /**
     * GET /api/questions/for-interview?search=kafka&category=Spring&size=100
     * Lightweight endpoint for interview creation question picker.
     * Returns a flat list (no pagination) with only fields needed for selection UI.
     */
    @GetMapping("/for-interview")
    public ResponseEntity<ApiResponse<List<QuestionDTO>>> getForInterview(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String importance,
            @RequestParam(defaultValue = "100") int size
    ) {
        List<QuestionDTO> questions = questionService.getQuestionsForInterview(search, category, importance, size);
        return ResponseEntity.ok(ApiResponse.ok(questions));
    }
}
