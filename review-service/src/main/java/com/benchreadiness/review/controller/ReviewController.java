package com.benchreadiness.review.controller;

import com.benchreadiness.review.security.StaffSecurityRoles;
import com.benchreadiness.review.feature.FeatureKey;
import com.benchreadiness.review.feature.RequiresFeature;
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

    /** GET /health/db — check database connectivity */
    @GetMapping("/health/db")
    public ResponseEntity<?> checkDatabaseHealth() {
        return ResponseEntity.ok(reviewService.checkDatabaseHealth());
    }

    /** POST /scores — save (replace) scores for an interview, called by ai-service after assessment */
    @PostMapping("/scores")
    public ResponseEntity<?> saveScores(@Valid @RequestBody SaveScoresRequest req) {
        return ResponseEntity.ok(reviewService.saveScores(req));
    }

    /** GET /reviews/{interviewId} — get sign-off for an interview */
    @GetMapping("/reviews/{interviewId}")
    @RequiresFeature(FeatureKey.REVIEW)
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

    /** POST /reviews/{interviewId}/sign-off — any staff role (Recruiter or Admin) can sign off an interview */
    @PostMapping("/reviews/{interviewId}/sign-off")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    @RequiresFeature(FeatureKey.REVIEW)
    public ResponseEntity<?> signOff(@PathVariable String interviewId,
                                      @Valid @RequestBody SignOffRequest req,
                                      @RequestHeader("X-User-Id") String userId,
                                      @RequestHeader("X-User-Role") String role) {
        req.setInterviewId(interviewId);
        try {
            SignOff signOff = reviewService.signOff(req, userId, role);
            return ResponseEntity.ok(signOff);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
