package com.benchreadiness.observer.controller;

import com.benchreadiness.observer.dto.ClientCreatedRequest;
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

    /** POST /observer/notify/client-created — notify admins when new client is created */
    @PostMapping("/notify/client-created")
    public ResponseEntity<?> notifyClientCreated(@Valid @RequestBody ClientCreatedRequest req) {
        try {
            emailService.sendClientCreatedNotification(
                req.getClientId(), 
                req.getClientName(), 
                req.getJdRole(),
                req.getBenchB2bCandidatesNeeded(), 
                req.getMarketCandidatesNeeded()
            );
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send notifications: " + e.getMessage()));
        }
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

    /** POST /observer/notify/interview-cancelled — notify candidate when interview is cancelled */
    @PostMapping("/notify/interview-cancelled")
    public ResponseEntity<?> notifyInterviewCancelled(@RequestBody Map<String, String> req) {
        try {
            String candidateEmail = req.get("candidateEmail");
            String candidateName = req.get("candidateName");
            String interviewId = req.get("interviewId");
            String jdTitle = req.get("jdTitle");
            String reason = req.get("reason");
            
            emailService.sendInterviewCancellation(candidateEmail, candidateName, interviewId, jdTitle, reason);
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

    /** POST /observer/inject — ADMIN or RECRUITER queues a follow-up question */
    @PostMapping("/inject")
    public ResponseEntity<?> inject(@Valid @RequestBody InjectRequest req,
                                     @RequestHeader("X-User-Id") String userId,
                                     @RequestHeader("X-User-Role") String role) {
        if (!role.equals("ADMIN") && !role.equals("SUPER_ADMIN") && !role.equals("RECRUITER")) {
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

    /** POST /observer/flag — ADMIN flags a candidate answer */
    @PostMapping("/flag")
    public ResponseEntity<?> flag(@Valid @RequestBody FlagRequest req,
                                   @RequestHeader("X-User-Id") String userId,
                                   @RequestHeader("X-User-Role") String role) {
        if (!role.equals("ADMIN") && !role.equals("SUPER_ADMIN")) {
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
