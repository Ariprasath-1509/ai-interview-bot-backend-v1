package com.benchreadiness.ai.controller;

import com.benchreadiness.ai.dto.AssessmentRequest;
import com.benchreadiness.ai.dto.NextQuestionRequest;
import com.benchreadiness.ai.dto.RubricRequest;
import com.benchreadiness.ai.service.AssessmentService;
import com.benchreadiness.ai.service.QuestionService;
import com.benchreadiness.ai.service.RubricService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final QuestionService questionService;
    private final AssessmentService assessmentService;
    private final RubricService rubricService;

    public AiController(QuestionService questionService, AssessmentService assessmentService, RubricService rubricService) {
        this.questionService = questionService;
        this.assessmentService = assessmentService;
        this.rubricService = rubricService;
    }

    @PostMapping("/next-question")
    public ResponseEntity<?> nextQuestion(@RequestBody NextQuestionRequest req) {
        QuestionService.QuestionResult result = questionService.getNextQuestion(req);
        return ResponseEntity.ok(Map.of(
            "question", result.question(),
            "manipulationDetected", result.manipulationDetected(),
            "terminateInterview", result.terminateInterview()
        ));
    }

    @PostMapping("/assess")
    public ResponseEntity<?> assess(@RequestBody AssessmentRequest req) {
        return ResponseEntity.ok(assessmentService.assess(req));
    }

    @PostMapping("/generate-rubric")
    public ResponseEntity<?> generateRubric(@RequestBody RubricRequest req) {
        return ResponseEntity.ok(rubricService.generateRubric(req));
    }
}
