package com.benchreadiness.ops.observer.controller;

import com.benchreadiness.ops.observer.dto.ClientCreatedRequest;
import com.benchreadiness.ops.observer.dto.FlagRequest;
import com.benchreadiness.ops.observer.dto.InjectRequest;
import com.benchreadiness.ops.observer.dto.InterviewAbandonedRequest;
import com.benchreadiness.ops.observer.dto.InterviewCreatedRequest;
import com.benchreadiness.ops.observer.entity.ObserverEvent;
import com.benchreadiness.ops.observer.service.EmailService;
import com.benchreadiness.ops.observer.service.ObserverService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/observer")
public class ObserverController {

    private final ObserverService observerService;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    public ObserverController(ObserverService observerService, SimpMessagingTemplate messagingTemplate,
                               EmailService emailService) {
        this.observerService = observerService;
        this.messagingTemplate = messagingTemplate;
        this.emailService = emailService;
    }

    @PostMapping("/notify/client-created")
    public ResponseEntity<?> notifyClientCreated(@Valid @RequestBody ClientCreatedRequest req) {
        try {
            emailService.sendClientCreatedNotification(req.getClientId(), req.getClientName(), req.getJdRole(),
                req.getBenchB2bCandidatesNeeded(), req.getMarketCandidatesNeeded());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send notifications: " + e.getMessage()));
        }
    }

    @PostMapping("/notify/interview-created")
    public ResponseEntity<?> notifyInterviewCreated(@Valid @RequestBody InterviewCreatedRequest req) {
        try {
            emailService.sendInterviewInvite(req.getEngineerEmail(), req.getEngineerName(), req.getInterviewId());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    @PostMapping("/notify/interview-abandoned")
    public ResponseEntity<?> notifyInterviewAbandoned(@Valid @RequestBody InterviewAbandonedRequest req) {
        try {
            emailService.sendInterviewAbandoned(req.getCreatedByUserId(), req.getInterviewId(), req.getReason());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    @PostMapping("/notify/interview-cancelled")
    public ResponseEntity<?> notifyInterviewCancelled(@RequestBody Map<String, String> req) {
        try {
            emailService.sendInterviewCancellation(req.get("candidateEmail"), req.get("candidateName"),
                req.get("interviewId"), req.get("jdTitle"), req.get("reason"));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    @GetMapping("/events/{interviewId}")
    public ResponseEntity<List<ObserverEvent>> getEvents(@PathVariable String interviewId,
                                                          @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(observerService.getEventsByInterview(interviewId, limit));
    }

    @PostMapping("/inject")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> inject(@Valid @RequestBody InjectRequest req,
                                     @RequestHeader("X-User-Id") String userId,
                                     @RequestHeader("X-User-Role") String role) {
        try {
            ObserverEvent event = observerService.inject(req, userId);
            messagingTemplate.convertAndSend("/topic/observer/" + req.getInterviewId(),
                Map.of("kind", event.getKind(), "payload", event.getPayloadJson(), "at", event.getCreatedAt().toString()));
            return ResponseEntity.ok(event);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/flag")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> flag(@Valid @RequestBody FlagRequest req,
                                   @RequestHeader("X-User-Id") String userId,
                                   @RequestHeader("X-User-Role") String role) {
        try {
            ObserverEvent event = observerService.flag(req, userId);
            messagingTemplate.convertAndSend("/topic/observer/" + req.getInterviewId(),
                Map.of("kind", event.getKind(), "payload", event.getPayloadJson(), "at", event.getCreatedAt().toString()));
            return ResponseEntity.ok(event);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
