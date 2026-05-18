package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.*;
import com.benchreadiness.interview.dto.AbandonInterviewRequest;
import com.benchreadiness.interview.dto.CreateInterviewRequest;
import com.benchreadiness.interview.entity.*;
import com.benchreadiness.interview.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class InterviewService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InterviewService.class);

    private final InterviewRepository interviewRepository;
    private final EngineerRepository engineerRepository;
    private final JobDescriptionRepository jdRepository;
    private final InterviewPlanRepository planRepository;
    private final ClientRepository clientRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final AiServiceClient aiServiceClient;
    private final ObserverServiceClient observerServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final AuthServiceClient authServiceClient;
    private final QuestionBankClient questionBankClient;

    public InterviewService(InterviewRepository interviewRepository,
                            EngineerRepository engineerRepository,
                            JobDescriptionRepository jdRepository,
                            InterviewPlanRepository planRepository,
                            ClientRepository clientRepository,
                            AiServiceClient aiServiceClient,
                            ObserverServiceClient observerServiceClient,
                            ComplianceServiceClient complianceServiceClient,
                            AuthServiceClient authServiceClient,
                            QuestionBankClient questionBankClient) {
        this.interviewRepository = interviewRepository;
        this.engineerRepository = engineerRepository;
        this.jdRepository = jdRepository;
        this.planRepository = planRepository;
        this.clientRepository = clientRepository;
        this.aiServiceClient = aiServiceClient;
        this.observerServiceClient = observerServiceClient;
        this.complianceServiceClient = complianceServiceClient;
        this.authServiceClient = authServiceClient;
        this.questionBankClient = questionBankClient;
    }

    @Transactional
    public Interview createInterview(CreateInterviewRequest req, String createdByUserId) throws Exception {
        // Check daily token limit before creating interview
        if (!checkTokenLimit(createdByUserId)) {
            throw new IllegalStateException("Daily token limit reached. No more interviews can be created today.");
        }
        // Upsert engineer by email
        Engineer engineer = engineerRepository.findByEmail(req.getEngineerEmail()).orElseGet(() -> {
            Engineer e = new Engineer();
            e.setUserId(req.getEngineerEmail());
            return e;
        });
        engineer.setEmail(req.getEngineerEmail());
        engineer.setName(req.getEngineerName());
        engineer = engineerRepository.save(engineer);

        // Create JD
        JobDescription jd = new JobDescription();
        jd.setTitle(req.getJdTitle());
        jd.setSource("paste");
        jd.setText(req.getJdText());
        jd = jdRepository.save(jd);

        // Build slot plan based on interview mode
        List<Map<String, Object>> slots = buildSlotsForMode(req.getInterviewMode());

        Map<String, Object> slotsDoc = new LinkedHashMap<>();
        slotsDoc.put("slots", slots);
        if (req.getFocusAreas() != null && !req.getFocusAreas().isBlank()) {
            slotsDoc.put("focusAreas", req.getFocusAreas().trim());
        }

        Map<String, Object> gapDoc = new LinkedHashMap<>();
        gapDoc.put("jdTitle", req.getJdTitle());
        gapDoc.put("inferredGaps", List.of());
        if (req.getResumeSummary() != null && !req.getResumeSummary().isBlank()) {
            gapDoc.put("resumeSummary", req.getResumeSummary().trim());
        }

        InterviewPlan plan = new InterviewPlan();
        plan.setEngineerId(engineer.getId());
        plan.setJdId(jd.getId());
        plan.setSlotsJson(objectMapper.writeValueAsString(slotsDoc));
        plan.setGapMapJson(objectMapper.writeValueAsString(gapDoc));
        plan.setCreatedByUserId(createdByUserId);
        plan = planRepository.save(plan);

        // Generate JD-driven rubric + candidate profile from ai-service
        try {
            Map<String, String> rubricRequest = Map.of(
                "jdTitle", req.getJdTitle(),
                "jdText", req.getJdText(),
                "resumeSummary", req.getResumeSummary() != null ? req.getResumeSummary() : "",
                "focusAreas", req.getFocusAreas() != null ? req.getFocusAreas() : ""
            );
            
            String rubricResponse = aiServiceClient.generateRubric(rubricRequest);
            
            if (rubricResponse != null) {
                // Parse and store rubricJson and candidateProfileJson separately
                com.fasterxml.jackson.databind.JsonNode rubricNode =
                    objectMapper.readTree(rubricResponse);
                plan.setRubricJson(objectMapper.writeValueAsString(rubricNode.path("rubric")));
                plan.setCandidateProfileJson(objectMapper.writeValueAsString(rubricNode.path("candidateProfile")));
                plan = planRepository.save(plan);
            }
        } catch (Exception e) {
            log.warn("Failed to generate rubric for plan {}: {}", plan.getId(), e.getMessage());
        }

        // Create interview
        Interview interview = new Interview();
        interview.setEngineerId(engineer.getId());
        interview.setJdId(jd.getId());
        interview.setPlanId(plan.getId());
        interview.setInterviewMode(req.getInterviewMode());
        interview.setCustomDurationMinutes(req.getCustomDurationMinutes());
        interview.setCreatedByUserId(createdByUserId);
        interview.setStatus(InterviewStatus.SCHEDULED);
        interview.setScheduledAt(Instant.now());
        
        // Fetch questions from question bank if clientId is provided
        if (req.getClientId() != null && !req.getClientId().isBlank()) {
            log.info("ClientId provided: {}, attempting to fetch questions from question bank", req.getClientId());
            try {
                UUID clientUuid = UUID.fromString(req.getClientId());
                Optional<Client> clientOpt = clientRepository.findById(clientUuid);
                if (clientOpt.isPresent()) {
                    String clientName = clientOpt.get().getClientName();
                    String clientSlug = clientName.toLowerCase().replaceAll("\\s+", "-");
                    String mode = req.getInterviewMode().name();
                    log.info("Fetching questions for client: {} (slug: {}), mode: {}", clientName, clientSlug, mode);
                    
                    com.fasterxml.jackson.databind.JsonNode questionsResponse = 
                        questionBankClient.fetchQuestionsByCompanyAndMode(clientSlug, mode);
                    
                    if (questionsResponse != null && questionsResponse.has("data") && 
                        questionsResponse.get("data").isArray() && 
                        questionsResponse.get("data").size() > 0) {
                        interview.setQuestionBankQuestionsJson(
                            objectMapper.writeValueAsString(questionsResponse.get("data"))
                        );
                        interview.setUsedQuestionIds("");
                        log.info("Fetched {} questions from question bank for client {} and mode {}", 
                            questionsResponse.get("data").size(), clientName, mode);
                    } else {
                        log.info("No questions found in question bank for client {} and mode {}", clientName, mode);
                    }
                } else {
                    log.warn("Client not found with ID: {}", req.getClientId());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch questions from question bank: {}", e.getMessage());
            }
        } else {
            log.info("No clientId provided, skipping question bank fetch");
        }
        
        Interview saved = interviewRepository.save(interview);

        // Notify observer-service to send invite email (fire-and-forget)
        try {
            Map<String, String> notificationRequest = Map.of(
                "interviewId", saved.getId(),
                "engineerEmail", req.getEngineerEmail(),
                "engineerName", req.getEngineerName() != null ? req.getEngineerName() : ""
            );
            observerServiceClient.notifyInterviewCreated(notificationRequest);
        } catch (Exception e) {
            log.warn("Failed to send interview invite email for {}: {}", saved.getId(), e.getMessage());
        }

        // Log audit trail
        String actorName = getUserName(createdByUserId);
        logAudit(createdByUserId, actorName, "ADMIN", "INTERVIEW_CREATED", saved.getId(),
            String.format("Created %s interview for %s - %s", 
                req.getInterviewMode(), req.getEngineerName(), req.getJdTitle()),
            null, null);

        return saved;
    }

    @Transactional
    public Interview abandonInterview(String id, AbandonInterviewRequest req) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));
        if (req.getTranscriptJson() != null) interview.setTranscriptJson(req.getTranscriptJson());
        interview.setStatus(InterviewStatus.COMPLETED);
        interview.setProposedVerdict(ReadinessVerdict.WITHDRAWN);
        interview.setEndedAt(Instant.now());
        Interview saved = interviewRepository.save(interview);

        // Notify observer-service to alert bench manager
        try {
            // Get createdByUserId from plan
            String createdByUserId = planRepository.findById(saved.getPlanId())
                    .map(InterviewPlan::getCreatedByUserId).orElse(null);
            if (createdByUserId != null) {
                Map<String, String> abandonRequest = Map.of(
                    "interviewId", saved.getId(),
                    "createdByUserId", createdByUserId,
                    "reason", req.getReason() != null ? req.getReason() : "not_prepared"
                );
                observerServiceClient.notifyInterviewAbandoned(abandonRequest);
            }
        } catch (Exception e) {
            log.warn("Failed to notify abandon for {}: {}", saved.getId(), e.getMessage());
        }

        // Log audit trail
        Engineer engineer = engineerRepository.findById(saved.getEngineerId()).orElse(null);
        String engineerName = engineer != null ? engineer.getName() : "Unknown";
        logAudit(saved.getEngineerId(), engineerName, "CANDIDATE", "INTERVIEW_ABANDONED", saved.getId(),
            String.format("Reason: %s", req.getReason() != null ? req.getReason() : "not_prepared"),
            null, null);

        return saved;
    }

    @Transactional
    public boolean deleteInterview(String id) {
        try {
            if (!interviewRepository.existsById(id)) {
                return false;
            }
            
            Interview interview = interviewRepository.findById(id).orElse(null);
            if (interview == null) {
                return false;
            }
            
            String planId = interview.getPlanId();
            String jdId = interview.getJdId();
            String engineerId = interview.getEngineerId();
            
            // Get engineer and JD details for email notification
            Engineer engineer = engineerRepository.findById(engineerId).orElse(null);
            JobDescription jd = jdRepository.findById(jdId).orElse(null);
            
            // Send cancellation email to candidate
            if (engineer != null && engineer.getEmail() != null) {
                try {
                    Map<String, String> cancelRequest = Map.of(
                        "candidateEmail", engineer.getEmail(),
                        "candidateName", engineer.getName() != null ? engineer.getName() : "Candidate",
                        "interviewId", id,
                        "jdTitle", jd != null ? jd.getTitle() : "Technical Interview",
                        "reason", "Interview cancelled by administrator"
                    );
                    observerServiceClient.notifyInterviewCancelled(cancelRequest);
                } catch (Exception e) {
                    log.warn("Failed to send cancellation email for interview {}: {}", id, e.getMessage());
                }
            }
            
            // Delete the interview first
            interviewRepository.deleteById(id);
            
            // Then delete related entities
            if (planId != null && planRepository.existsById(planId)) {
                planRepository.deleteById(planId);
            }
            
            if (jdId != null && jdRepository.existsById(jdId)) {
                jdRepository.deleteById(jdId);
            }
            
            log.info("Successfully deleted interview {} and related entities", id);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete interview {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete interview: " + e.getMessage());
        }
    }

    public Optional<Interview> findById(String id) {
        return interviewRepository.findById(id);
    }

    public List<Interview> findAll() {
        return interviewRepository.findAll();
    }

    public List<com.benchreadiness.interview.dto.InterviewSummaryDto> getSummaries() {
        return interviewRepository.findAll().stream().map(interview -> {
            Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
            JobDescription jd = jdRepository.findById(interview.getJdId()).orElse(null);
            String candidateName = engineer != null && engineer.getName() != null && !engineer.getName().isBlank()
                    ? engineer.getName()
                    : (engineer != null ? engineer.getEmail() : "Unknown");
            String candidateEmail = engineer != null ? engineer.getEmail() : "";
            String jdTitle = jd != null ? jd.getTitle() : "";
            String proposedVerdict = interview.getProposedVerdict() != null ? interview.getProposedVerdict().name() : null;
            String finalVerdict = interview.getFinalVerdict() != null ? interview.getFinalVerdict().name() : null;
            return new com.benchreadiness.interview.dto.InterviewSummaryDto(
                    interview.getId(), interview.getStatus().name(), proposedVerdict, finalVerdict,
                    candidateName, candidateEmail, jdTitle,
                    interview.getCreatedAt() != null ? interview.getCreatedAt().toString() : "",
                    interview.getInterviewMode() != null ? interview.getInterviewMode().name() : "SCREENING"
            );
        }).toList();
    }

    public List<Interview> findByEmail(String email) {
        return engineerRepository.findByEmail(email)
                .map(e -> interviewRepository.findByEngineerId(e.getId()))
                .orElse(List.of());
    }

    public List<com.benchreadiness.interview.dto.InterviewSummaryDto> getTodaysSummaries() {
        java.time.LocalDate today = java.time.LocalDate.now();
        Instant startOfDay = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        List<Interview> todayInterviews = interviewRepository.findCreatedToday(startOfDay, endOfDay);
        return todayInterviews.stream().map(interview -> {
            Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
            JobDescription jd = jdRepository.findById(interview.getJdId()).orElse(null);
            String candidateName = engineer != null && engineer.getName() != null && !engineer.getName().isBlank()
                    ? engineer.getName() : (engineer != null ? engineer.getEmail() : "Unknown");
            String candidateEmail = engineer != null ? engineer.getEmail() : "";
            String jdTitle = jd != null ? jd.getTitle() : "";
            String proposedVerdict = interview.getProposedVerdict() != null ? interview.getProposedVerdict().name() : null;
            String finalVerdict = interview.getFinalVerdict() != null ? interview.getFinalVerdict().name() : null;
            return new com.benchreadiness.interview.dto.InterviewSummaryDto(
                    interview.getId(), interview.getStatus().name(), proposedVerdict, finalVerdict,
                    candidateName, candidateEmail, jdTitle,
                    interview.getCreatedAt() != null ? interview.getCreatedAt().toString() : "",
                    interview.getInterviewMode() != null ? interview.getInterviewMode().name() : "SCREENING"
            );
        }).toList();
    }

    public Optional<JobDescription> findJdById(String jdId) {
        return jdRepository.findById(jdId);
    }

    public Optional<InterviewPlan> findPlanById(String planId) {
        return planRepository.findById(planId);
    }

    @Transactional
    public Interview completeInterview(String id, com.benchreadiness.interview.dto.CompleteInterviewRequest req) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));
        if (req.getTranscriptJson() != null) interview.setTranscriptJson(req.getTranscriptJson());
        if (req.getStatus() != null) {
            try { interview.setStatus(InterviewStatus.valueOf(req.getStatus())); } catch (Exception ignored) {}
        }
        if (req.getProposedVerdict() != null) {
            try { interview.setProposedVerdict(ReadinessVerdict.valueOf(req.getProposedVerdict())); } catch (Exception ignored) {}
        }
        if (req.getFinalVerdict() != null) {
            try { interview.setFinalVerdict(ReadinessVerdict.valueOf(req.getFinalVerdict())); } catch (Exception ignored) {}
        }
        interview.setEndedAt(Instant.now());
        Interview saved = interviewRepository.save(interview);
        
        // Increment system interview count for the candidate
        if (interview.getStatus() == InterviewStatus.COMPLETED || interview.getStatus() == InterviewStatus.SIGNED_OFF) {
            try {
                Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
                if (engineer != null && engineer.getEmail() != null) {
                    // Find candidate by email and increment count
                    authServiceClient.incrementSystemInterviewCountByEmail(engineer.getEmail());
                }
            } catch (Exception e) {
                log.warn("Failed to increment system interview count for interview {}: {}", id, e.getMessage());
            }
        }
        
        // Log audit trail
        Engineer engineer = engineerRepository.findById(saved.getEngineerId()).orElse(null);
        String engineerName = engineer != null ? engineer.getName() : "Unknown";
        logAudit(saved.getEngineerId(), engineerName, "CANDIDATE", "INTERVIEW_COMPLETED", saved.getId(),
            String.format("Status: %s, Verdict: %s", saved.getStatus(), 
                saved.getProposedVerdict() != null ? saved.getProposedVerdict() : "N/A"),
            null, null);
        
        return saved;
    }

    @Transactional
    public Interview updateInterview(String id, Map<String, String> updates) {
        log.info("Updating interview {} with updates: {}", id, updates);
        
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));
        
        if (updates.containsKey("status")) {
            try { 
                InterviewStatus newStatus = InterviewStatus.valueOf(updates.get("status"));
                log.info("Updating interview {} status from {} to {}", id, interview.getStatus(), newStatus);
                interview.setStatus(newStatus);
            } catch (Exception e) {
                log.error("Failed to parse status: {}", updates.get("status"), e);
            }
        }
        if (updates.containsKey("finalVerdict")) {
            try { 
                ReadinessVerdict newVerdict = ReadinessVerdict.valueOf(updates.get("finalVerdict"));
                log.info("Updating interview {} finalVerdict to {}", id, newVerdict);
                interview.setFinalVerdict(newVerdict);
            } catch (Exception e) {
                log.error("Failed to parse finalVerdict: {}", updates.get("finalVerdict"), e);
            }
        }
        if (updates.containsKey("proposedVerdict")) {
            try { 
                interview.setProposedVerdict(ReadinessVerdict.valueOf(updates.get("proposedVerdict")));
            } catch (Exception ignored) {}
        }
        if (updates.containsKey("usedQuestionIds")) {
            interview.setUsedQuestionIds(updates.get("usedQuestionIds"));
            log.info("Updated usedQuestionIds for interview {}: {}", id, updates.get("usedQuestionIds"));
        }
        
        Interview saved = interviewRepository.save(interview);
        log.info("Interview {} updated successfully. New status: {}, finalVerdict: {}", 
            id, saved.getStatus(), saved.getFinalVerdict());
        
        // Increment system interview count when interview is signed off
        if (updates.containsKey("status") && "SIGNED_OFF".equals(updates.get("status"))) {
            try {
                Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
                if (engineer != null && engineer.getEmail() != null) {
                    log.info("Incrementing system interview count for candidate: {}", engineer.getEmail());
                    authServiceClient.incrementSystemInterviewCountByEmail(engineer.getEmail());
                    log.info("Successfully incremented system interview count for: {}", engineer.getEmail());
                }
            } catch (Exception e) {
                log.warn("Failed to increment system interview count for interview {}: {}", id, e.getMessage());
            }
        }
        
        // Log audit trail for status changes
        if (updates.containsKey("status") || updates.containsKey("finalVerdict")) {
            String detail = "";
            if (updates.containsKey("status")) {
                detail += "Status: " + updates.get("status");
            }
            if (updates.containsKey("finalVerdict")) {
                if (!detail.isEmpty()) detail += ", ";
                detail += "Final Verdict: " + updates.get("finalVerdict");
            }
            logAudit("system", "System", "SYSTEM", "INTERVIEW_STATUS_CHANGED", saved.getId(),
                detail, null, null);
        }
        
        return saved;
    }

    private Map<String, Object> slot(int num, String theme, String difficulty, int minutes) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("slot", num);
        s.put("theme", theme);
        s.put("difficulty", difficulty);
        s.put("minutes", minutes);
        return s;
    }

    private List<Map<String, Object>> buildSlotsForMode(InterviewMode mode) {
        return switch (mode) {
            case SCREENING -> List.of(
                slot(1, "Basic intro — what they've built, primary stack", "easy", 3),
                slot(2, "Core concept check — one fundamental topic", "easy", 3),
                slot(3, "Simple problem solving", "easy", 3),
                slot(4, "Communication — explain something technical simply", "easy", 3),
                slot(5, "Role fit — why this role, what they want to learn", "easy", 3)
            );
            case L1 -> List.of(
                slot(1, "Technical opener — recent project walkthrough", "easy", 3),
                slot(2, "Core skills — fundamentals depth", "easy", 3),
                slot(3, "Problem solving — basic algorithm or logic", "easy-medium", 3),
                slot(4, "Implementation details — how they build things", "easy-medium", 3),
                slot(5, "Testing & quality — their approach to correctness", "easy-medium", 3),
                slot(6, "Learning & growth — how they stay current", "easy", 3),
                slot(7, "Team collaboration — working with others", "easy", 2)
            );
            case L2 -> List.of(
                slot(1, "System overview — architecture they've worked on", "medium", 3),
                slot(2, "Trade-offs & decisions — competing concerns", "medium", 3),
                slot(3, "Real-world scenarios — production challenges", "medium", 3),
                slot(4, "Data & consistency — how they handle state", "medium", 3),
                slot(5, "Performance & scale — optimization experience", "medium", 3),
                slot(6, "Debugging & troubleshooting — incident response", "medium", 3),
                slot(7, "Design patterns — when and why to use them", "medium", 3),
                slot(8, "Integration challenges — working with external systems", "medium", 4)
            );
            case L3 -> List.of(
                slot(1, "Architecture design — system they've architected", "medium-hard", 3),
                slot(2, "Distributed systems — consistency, availability, partition tolerance", "medium-hard", 3),
                slot(3, "Failure handling — cascading failures, circuit breakers", "medium-hard", 3),
                slot(4, "Performance at scale — bottlenecks and optimization", "medium-hard", 3),
                slot(5, "Data architecture — storage, caching, replication", "medium-hard", 3),
                slot(6, "Monitoring & observability — how they instrument systems", "medium-hard", 3),
                slot(7, "Security considerations — threat modeling, defense", "medium-hard", 3),
                slot(8, "Technical leadership — influencing technical decisions", "medium-hard", 3),
                slot(9, "System evolution — refactoring large systems", "medium-hard", 3),
                slot(10, "Complex problem solving — ambiguous technical challenges", "medium-hard", 3)
            );
            case L4 -> List.of(
                slot(1, "System design at scale — design a distributed system", "hard", 3),
                slot(2, "Architecture trade-offs — CAP theorem, consistency models", "hard", 3),
                slot(3, "Failure handling — chaos engineering, resilience patterns", "hard", 3),
                slot(4, "Cross-team impact — how they influenced architecture decisions", "hard", 3),
                slot(5, "Ambiguity handling — vague requirement to concrete plan", "hard", 3),
                slot(6, "Technical strategy — long-term technical vision", "hard", 3),
                slot(7, "Organizational scaling — technical decisions across teams", "hard", 3),
                slot(8, "Innovation & research — exploring new technologies", "hard", 3),
                slot(9, "Mentorship & growth — developing other engineers", "hard", 3),
                slot(10, "Business impact — connecting technical decisions to outcomes", "hard", 3)
            );
        };
    }

    private boolean checkTokenLimit(String userId) {
        try {
            Map<String, Object> response = complianceServiceClient.checkTokenLimit(userId);
            return (Boolean) response.getOrDefault("canProceed", true);
        } catch (Exception e) {
            log.warn("Failed to check token limit for user {}: {}", userId, e.getMessage());
            return true;
        }
    }

    private String getUserName(String userId) {
        try {
            Map<String, Object> user = authServiceClient.getUserById(userId);
            return (String) user.getOrDefault("name", "Unknown");
        } catch (Exception e) {
            log.warn("Failed to fetch user name for {}: {}", userId, e.getMessage());
            return "Unknown";
        }
    }

    private void logAudit(String actorId, String actorName, String actorRole, String action, 
                         String resourceId, String detail, String oldValue, String newValue) {
        try {
            Map<String, Object> auditLog = new HashMap<>();
            auditLog.put("actorId", actorId);
            if (actorName != null) auditLog.put("actorName", actorName);
            auditLog.put("actorRole", actorRole);
            auditLog.put("action", action);
            auditLog.put("resource", "INTERVIEW");
            auditLog.put("resourceId", resourceId);
            if (detail != null) auditLog.put("detail", detail);
            if (oldValue != null) auditLog.put("oldValue", oldValue);
            if (newValue != null) auditLog.put("newValue", newValue);
            auditLog.put("ipAddress", "system");
            complianceServiceClient.recordAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> recalculateAllSystemInterviewCounts() {
        try {
            log.info("Starting recalculation of system interview counts for all candidates");
            
            // Get all completed and signed-off interviews grouped by candidate email
            List<Interview> completedInterviews = interviewRepository.findByStatusIn(
                List.of(InterviewStatus.COMPLETED, InterviewStatus.SIGNED_OFF)
            );
            
            log.info("Found {} completed/signed-off interviews", completedInterviews.size());
            
            Map<String, Integer> emailToCount = new HashMap<>();
            
            for (Interview interview : completedInterviews) {
                try {
                    Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
                    if (engineer != null && engineer.getEmail() != null) {
                        String email = engineer.getEmail();
                        emailToCount.put(email, emailToCount.getOrDefault(email, 0) + 1);
                        log.debug("Counted interview {} for candidate {}", interview.getId(), email);
                    } else {
                        log.warn("Engineer not found or has no email for interview {}", interview.getId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to process interview {} for count calculation: {}", interview.getId(), e.getMessage());
                }
            }
            
            log.info("Calculated counts for {} candidates: {}", emailToCount.size(), emailToCount);
            
            // For now, just return the calculated counts without updating
            // This will help us see what the counts should be
            return Map.of(
                "ok", true,
                "message", "System interview counts calculated successfully",
                "totalInterviews", completedInterviews.size(),
                "candidatesFound", emailToCount.size(),
                "candidateCountMap", emailToCount,
                "note", "Counts calculated but not updated yet - check logs for details"
            );
            
        } catch (Exception e) {
            log.error("Failed to recalculate system interview counts: {}", e.getMessage(), e);
            return Map.of(
                "ok", false,
                "error", "Failed to recalculate counts: " + e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            );
        }
    }
}
