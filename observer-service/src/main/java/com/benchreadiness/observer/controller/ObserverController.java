package com.benchreadiness.observer.controller;

import com.benchreadiness.observer.dto.FlagRequest;
import com.benchreadiness.observer.dto.InjectRequest;
import com.benchreadiness.observer.dto.InterviewAbandonedRequest;
import com.benchreadiness.observer.dto.InterviewCreatedRequest;
import com.benchreadiness.observer.entity.ObserverEvent;
import com.benchreadiness.observer.service.EmailService;
import com.benchreadiness.observer.service.ObserverService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/observer")
public class ObserverController {

    private final ObserverService observerService;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    public ObserverController(ObserverService observerService, SimpMessagingTemplate messagingTemplate, EmailService emailService) {
        this.observerService = observerService;
        this.messagingTemplate = messagingTemplate;
        this.emailService = emailService;
    }

    /** POST /observer/notify/interview-created — send invite email to candidate */
    @PostMapping("/notify/interview-created")
    public ResponseEntity<?> notifyInterviewCreated(@Valid @RequestBody InterviewCreatedRequest req) {
        try {
            emailService.sendInterviewInvite(req.getEngineerEmail(), req.getEngineerName(), req.getInterviewId());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    /** POST /observer/notify/interview-abandoned — alert bench manager when candidate exits early */
    @PostMapping("/notify/interview-abandoned")
    public ResponseEntity<?> notifyInterviewAbandoned(@Valid @RequestBody InterviewAbandonedRequest req) {
        try {
            emailService.sendInterviewAbandoned(req.getCreatedByUserId(), req.getInterviewId(), req.getReason());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    /** GET /observer/events/{interviewId} — list latest events for an interview */
    @GetMapping("/events/{interviewId}")
    public ResponseEntity<List<ObserverEvent>> getEvents(
            @PathVariable String interviewId,
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(observerService.getEventsByInterview(interviewId, limit));
    }

    /** POST /observer/inject — BENCH_MANAGER or PRACTICE_LEAD queues a follow-up question */
    @PostMapping("/inject")
    public ResponseEntity<?> inject(@Valid @RequestBody InjectRequest req,
                                     @RequestHeader("X-User-Id") String userId,
                                     @RequestHeader("X-User-Role") String role) {
        if (!role.equals("BENCH_MANAGER") && !role.equals("INTERVIEWER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        try {
            ObserverEvent event = observerService.inject(req, userId);
            // Push to WebSocket subscribers watching this interview
            messagingTemplate.convertAndSend(
                "/topic/observer/" + req.getInterviewId(),
                Map.of("kind", event.getKind(), "payload", event.getPayloadJson(), "at", event.getCreatedAt().toString())
            );
            return ResponseEntity.ok(event);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /observer/flag — BENCH_MANAGER flags a candidate answer */
    @PostMapping("/flag")
    public ResponseEntity<?> flag(@Valid @RequestBody FlagRequest req,
                                   @RequestHeader("X-User-Id") String userId,
                                   @RequestHeader("X-User-Role") String role) {
        if (!role.equals("BENCH_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        try {
            ObserverEvent event = observerService.flag(req, userId);
            messagingTemplate.convertAndSend(
                "/topic/observer/" + req.getInterviewId(),
                Map.of("kind", event.getKind(), "payload", event.getPayloadJson(), "at", event.getCreatedAt().toString())
            );
            return ResponseEntity.ok(event);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
