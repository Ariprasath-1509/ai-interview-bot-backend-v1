package com.qb.core.controller;

import com.qb.core.dto.ApiResponse;
import com.qb.core.dto.EmailLogDTO;
import com.qb.core.entity.EmailLog;
import com.qb.core.repository.EmailLogRepository;
import com.qb.core.service.EmailService;
import com.qb.core.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService       emailService;
    private final QuestionService    questionService;
    private final EmailLogRepository emailLogRepo;

    /**
     * Send interview prep emails.
     * Body: {
     *   recipients: [{email, name, company, round, date}],
     *   filters: {company?, round?, category?, importance?}
     * }
     */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> send(
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<Map<String, String>> recipients = (List<Map<String, String>>) body.get("recipients");

        @SuppressWarnings("unchecked")
        Map<String, String> filters = body.containsKey("filters")
                ? (Map<String, String>) body.get("filters") : Map.of();

        // Fetch questions based on filters
        var questions = questionService.searchQuestions(
                null,
                filters.get("interviewType"),
                filters.get("category"),
                filters.get("company"),
                filters.get("round"),
                null,
                filters.get("importance"),
                0, 100
        ).getContent();

        var result = emailService.sendInterviewPrepEmails(
                UUID.fromString(userId),
                recipients,
                questions,
                filters
        );

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "emailsSent", result.emailsSent(),
                "errors", result.errors()
        )));
    }

    /**
     * Get email logs with optional filters.
     * Query params: sentBy (uuid), from (date), to (date), page, size
     */
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<EmailLogDTO>>> getLogs(
            @RequestParam(required = false) String sentBy,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID sentByUuid = sentBy != null ? UUID.fromString(sentBy) : null;
        Instant fromInstant = from != null ? LocalDate.parse(from).atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant toInstant   = to   != null ? LocalDate.parse(to).atTime(23, 59, 59).toInstant(ZoneOffset.UTC) : null;

        Page<EmailLogDTO> logs = emailLogRepo
                .findFiltered(sentByUuid, fromInstant, toInstant, PageRequest.of(page, size))
                .map(this::toDTO);

        return ResponseEntity.ok(ApiResponse.ok(logs));
    }

    private EmailLogDTO toDTO(EmailLog e) {
        return new EmailLogDTO(
                e.getId(), e.getSentBy(), e.getSubject(),
                e.getRecipientCount(), e.getRecipientEmails(),
                e.getFilters(), e.getSentAt()
        );
    }
}
