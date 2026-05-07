package com.benchreadiness.review.controller;

import com.benchreadiness.review.dto.SaveScoresRequest;
import com.benchreadiness.review.dto.SignOffRequest;
import com.benchreadiness.review.entity.Score;
import com.benchreadiness.review.entity.SignOff;
import com.benchreadiness.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** GET /scores/{interviewId} — fetch all scores for an interview */
    @GetMapping("/scores/{interviewId}")
    public ResponseEntity<List<Score>> getScores(@PathVariable String interviewId) {
        return ResponseEntity.ok(reviewService.getScores(interviewId));
    }

    /** POST /scores — save (replace) scores for an interview, called by ai-service after assessment */
    @PostMapping("/scores")
    public ResponseEntity<?> saveScores(@Valid @RequestBody SaveScoresRequest req) {
        return ResponseEntity.ok(reviewService.saveScores(req));
    }

    /** GET /reviews/{interviewId} — get sign-off for an interview */
    @GetMapping("/reviews/{interviewId}")
    public ResponseEntity<?> getReview(@PathVariable String interviewId) {
        SignOff signOff = reviewService.getSignOff(interviewId);
        if (signOff == null) return ResponseEntity.ok(Map.of("signedOff", false));
        return ResponseEntity.ok(Map.of(
            "signedOff", true,
            "finalVerdict", signOff.getFinalVerdict().name(),
            "note", signOff.getNote(),
            "signedOffAt", signOff.getSignedOffAt().toString(),
            "reviewerUserId", signOff.getReviewerUserId()
        ));
    }

    /** POST /reviews/{interviewId}/sign-off — ADMIN signs off an interview */
    @PostMapping("/reviews/{interviewId}/sign-off")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> signOff(@PathVariable String interviewId,
                                      @Valid @RequestBody SignOffRequest req,
                                      @RequestHeader("X-User-Id") String userId,
                                      @RequestHeader("X-User-Role") String role) {
        req.setInterviewId(interviewId);
        try {
            SignOff signOff = reviewService.signOff(req, userId);
            return ResponseEntity.ok(signOff);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
