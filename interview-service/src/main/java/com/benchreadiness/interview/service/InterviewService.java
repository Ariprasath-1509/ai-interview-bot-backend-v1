package com.benchreadiness.interview.service;

import com.benchreadiness.interview.branch.InterviewBranchFilter;
import com.benchreadiness.interview.branch.InterviewCandidateBranchLookup;
import com.benchreadiness.interview.client.*;
import com.benchreadiness.interview.dto.AbandonInterviewRequest;
import com.benchreadiness.interview.dto.BulkCreateInterviewRequest;
import com.benchreadiness.interview.dto.BulkCreateInterviewResult;
import com.benchreadiness.interview.dto.CreateInterviewRequest;
import com.benchreadiness.interview.entity.*;
import com.benchreadiness.interview.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final InterviewProctoringSupport proctoringSupport;
    private final InterviewCandidateBranchLookup candidateBranchLookup;
    private final Executor bulkInterviewExecutor;

    public InterviewService(InterviewRepository interviewRepository,
                            EngineerRepository engineerRepository,
                            JobDescriptionRepository jdRepository,
                            InterviewPlanRepository planRepository,
                            ClientRepository clientRepository,
                            AiServiceClient aiServiceClient,
                            ObserverServiceClient observerServiceClient,
                            ComplianceServiceClient complianceServiceClient,
                            AuthServiceClient authServiceClient,
                            QuestionBankClient questionBankClient,
                            InterviewProctoringSupport proctoringSupport,
                            InterviewCandidateBranchLookup candidateBranchLookup,
                            @Qualifier("bulkInterviewExecutor") Executor bulkInterviewExecutor) {
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
        this.proctoringSupport = proctoringSupport;
        this.candidateBranchLookup = candidateBranchLookup;
        this.bulkInterviewExecutor = bulkInterviewExecutor;
    }

    public Interview createInterview(CreateInterviewRequest req, String createdByUserId,
                                     String branch, java.util.UUID clientId) throws Exception {
        // Step 1: Do all external HTTP calls BEFORE opening the DB transaction
        // This prevents holding a DB connection open while waiting for slow external services

        // Check token limit (fast, compliance-service)
        if (!checkTokenLimit(createdByUserId)) {
            throw new IllegalStateException("Daily token limit reached. No more interviews can be created today.");
        }

        // Generate rubric from ai-service (slow — can take 30-60s, must be outside transaction)
        String rubricJson = null;
        String candidateProfileJson = null;
        try {
            Map<String, String> rubricRequest = Map.of(
                "jdTitle", req.getJdTitle(),
                "jdText", req.getJdText() != null ? req.getJdText() : "",
                "resumeSummary", req.getResumeSummary() != null ? req.getResumeSummary() : "",
                "focusAreas", req.getFocusAreas() != null ? req.getFocusAreas() : ""
            );
            String rubricResponse = aiServiceClient.generateRubric(rubricRequest);
            if (rubricResponse != null) {
                com.fasterxml.jackson.databind.JsonNode rubricNode = objectMapper.readTree(rubricResponse);
                rubricJson = objectMapper.writeValueAsString(rubricNode.path("rubric"));
                candidateProfileJson = objectMapper.writeValueAsString(rubricNode.path("candidateProfile"));
            }
        } catch (Exception e) {
            log.warn("Failed to generate rubric (will proceed without it): {}", e.getMessage());
        }

        // Fetch question bank questions (slow — can time out, must be outside transaction)
        String questionBankQuestionsJson = null;
        if (req.getSelectedQuestionIds() != null && !req.getSelectedQuestionIds().isBlank()) {
            log.info("selectedQuestionIds provided, resolving questions from question bank");
            try {
                Set<String> selectedIdSet = java.util.Arrays.stream(
                    req.getSelectedQuestionIds().split(","))
                    .map(String::trim).filter(s -> !s.isBlank())
                    .collect(java.util.stream.Collectors.toSet());
                com.fasterxml.jackson.databind.JsonNode resp =
                    questionBankClient.fetchQuestionsForInterview(null, null, 200);
                if (resp != null && resp.has("data") && resp.get("data").isArray()) {
                    List<com.fasterxml.jackson.databind.JsonNode> filtered = new ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode q : resp.get("data")) {
                        if (selectedIdSet.contains(q.path("id").asText())) filtered.add(q);
                    }
                    if (!filtered.isEmpty()) {
                        questionBankQuestionsJson = objectMapper.writeValueAsString(filtered);
                        log.info("Resolved {} admin-selected questions", filtered.size());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve selectedQuestionIds: {}", e.getMessage());
            }
        } else if (req.getClientId() != null && !req.getClientId().isBlank()) {
            try {
                UUID clientUuid = UUID.fromString(req.getClientId());
                Optional<Client> clientOpt = clientRepository.findById(clientUuid);
                if (clientOpt.isPresent()) {
                    String clientSlug = clientOpt.get().getClientName().toLowerCase().replaceAll("\\s+", "-");
                    String mode = req.getInterviewMode().name();
                    com.fasterxml.jackson.databind.JsonNode questionsResponse =
                        questionBankClient.fetchQuestionsByCompanyAndMode(clientSlug, mode);
                    if (questionsResponse != null && questionsResponse.has("data")
                            && questionsResponse.get("data").isArray()
                            && questionsResponse.get("data").size() > 0) {
                        questionBankQuestionsJson = objectMapper.writeValueAsString(questionsResponse.get("data"));
                        log.info("Fetched {} questions from question bank", questionsResponse.get("data").size());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch questions from question bank: {}", e.getMessage());
            }
        }

        // Step 2: Now do all DB work in a single short transaction
        boolean includeProgramming = req.getIncludeProgrammingQuestions() == null
            || Boolean.TRUE.equals(req.getIncludeProgrammingQuestions());
        if (!includeProgramming && questionBankQuestionsJson != null) {
            questionBankQuestionsJson = filterProgrammingFromQuestionBankJson(questionBankQuestionsJson);
        }
        return persistInterview(req, createdByUserId, rubricJson, candidateProfileJson, questionBankQuestionsJson,
                branch, clientId);
    }

    @Transactional
    protected Interview persistInterview(CreateInterviewRequest req, String createdByUserId,
                                         String rubricJson, String candidateProfileJson,
                                         String questionBankQuestionsJson,
                                         String branch, java.util.UUID clientId) throws Exception {
        // Upsert engineer
        Engineer engineer = engineerRepository.findByEmail(req.getEngineerEmail()).orElseGet(() -> {
            Engineer e = new Engineer();
            e.setUserId(req.getEngineerEmail());
            return e;
        });
        engineer.setEmail(req.getEngineerEmail());
        if (req.getEngineerName() != null && !req.getEngineerName().isBlank()
                && (engineer.getName() == null || engineer.getName().isBlank())) {
            engineer.setName(req.getEngineerName());
        }
        engineer = engineerRepository.save(engineer);

        // Create JD
        JobDescription jd = new JobDescription();
        jd.setTitle(req.getJdTitle());
        jd.setSource("paste");
        jd.setText(req.getJdText());
        jd = jdRepository.save(jd);

        // Build plan
        Map<String, Object> slotsDoc = new LinkedHashMap<>();
        slotsDoc.put("slots", buildSlotsForMode(req.getInterviewMode()));
        if (req.getFocusAreas() != null && !req.getFocusAreas().isBlank())
            slotsDoc.put("focusAreas", req.getFocusAreas().trim());

        Map<String, Object> gapDoc = new LinkedHashMap<>();
        gapDoc.put("jdTitle", req.getJdTitle());
        gapDoc.put("inferredGaps", List.of());
        if (req.getResumeSummary() != null && !req.getResumeSummary().isBlank())
            gapDoc.put("resumeSummary", req.getResumeSummary().trim());

        InterviewPlan plan = new InterviewPlan();
        plan.setEngineerId(engineer.getId());
        plan.setJdId(jd.getId());
        plan.setSlotsJson(objectMapper.writeValueAsString(slotsDoc));
        plan.setGapMapJson(objectMapper.writeValueAsString(gapDoc));
        plan.setCreatedByUserId(createdByUserId);
        if (rubricJson != null) plan.setRubricJson(rubricJson);
        if (candidateProfileJson != null) plan.setCandidateProfileJson(candidateProfileJson);
        plan = planRepository.save(plan);

        // Resolve proctoringMode at creation so it's persisted and available without auth-service on subsequent reads
        String candidateSource = proctoringSupport.resolveCandidateSource(engineer);
        String resolvedProctoringMode = proctoringSupport.resolveProctoringMode(candidateSource);

        // Create interview
        Interview interview = new Interview();
        interview.setEngineerId(engineer.getId());
        interview.setJdId(jd.getId());
        interview.setPlanId(plan.getId());
        interview.setInterviewMode(req.getInterviewMode());
        interview.setRoundName(req.getRoundName());
        interview.setCustomDurationMinutes(req.getCustomDurationMinutes());
        interview.setCreatedByUserId(createdByUserId);
        interview.setBranch(branch != null ? branch : com.benchreadiness.interview.branch.BranchAccess.defaultBranch());
        interview.setClientId(clientId);
        interview.setProctoringMode(resolvedProctoringMode);
        interview.setStatus(InterviewStatus.SCHEDULED);
        interview.setScheduledAt(req.getScheduledAt() != null ? req.getScheduledAt() : Instant.now());
        interview.setExpiresAt(req.getExpiresAt());
        boolean includeProgramming = req.getIncludeProgrammingQuestions() == null
            || Boolean.TRUE.equals(req.getIncludeProgrammingQuestions());
        interview.setIncludeProgrammingQuestions(includeProgramming);
        if (questionBankQuestionsJson != null) {
            interview.setQuestionBankQuestionsJson(questionBankQuestionsJson);
            interview.setUsedQuestionIds("");
        }
        
        // Store custom questions if provided
        if (req.getCustomQuestions() != null && !req.getCustomQuestions().isEmpty()) {
            try {
                interview.setCustomQuestionsJson(objectMapper.writeValueAsString(req.getCustomQuestions()));
                log.info("Stored {} custom questions for interview", req.getCustomQuestions().size());
            } catch (Exception e) {
                log.warn("Failed to serialize custom questions: {}", e.getMessage());
            }
        }
        
        Interview saved = interviewRepository.save(interview);

        // Activate market candidate credentials when interview is scheduled
        try {
            Map<String, Object> candidateProfile = authServiceClient.getUserByEmail(engineer.getEmail());
            if (candidateProfile != null && "MARKET".equals(candidateProfile.get("source"))) {
                String candidateUserId = (String) candidateProfile.get("id");
                if (candidateUserId != null) {
                    authServiceClient.setUserActive(candidateUserId, Map.of("active", true));
                    log.info("Activated market candidate {} for interview {}", candidateUserId, saved.getId());
                }
            }
        } catch (Exception e) {
            log.warn("Could not activate market candidate for interview {}: {}", saved.getId(), e.getMessage());
        }

        // Notify candidate: interview scheduled email
        try {
            java.time.format.DateTimeFormatter emailFmt =
                java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' hh:mm a 'UTC'")
                    .withZone(java.time.ZoneOffset.UTC);
            Map<String, String> notifyBody = new java.util.LinkedHashMap<>();
            notifyBody.put("email", engineer.getEmail());
            notifyBody.put("name", engineer.getName() != null ? engineer.getName() : "");
            notifyBody.put("scheduledAt", emailFmt.format(saved.getScheduledAt()));
            notifyBody.put("expiresAt", saved.getExpiresAt() != null ? emailFmt.format(saved.getExpiresAt()) : null);
            authServiceClient.notifyInterviewScheduled(notifyBody);
            log.info("Interview scheduled notification sent to {}", engineer.getEmail());
        } catch (Exception e) {
            log.warn("Failed to send interview scheduled email for {}: {}", saved.getId(), e.getMessage());
        }

        // Fire-and-forget: invite email
        try {
            observerServiceClient.notifyInterviewCreated(Map.of(
                "interviewId", saved.getId(),
                "engineerEmail", req.getEngineerEmail(),
                "engineerName", req.getEngineerName() != null ? req.getEngineerName() : ""
            ));
        } catch (Exception e) {
            log.warn("Failed to send interview invite email for {}: {}", saved.getId(), e.getMessage());
        }

        // Audit log (fire-and-forget)
        try {
            String actorName = getUserName(createdByUserId);
            logAudit(createdByUserId, actorName, "ADMIN", "INTERVIEW_CREATED", saved.getId(),
                String.format("Created %s interview for %s - %s",
                    req.getInterviewMode(), req.getEngineerName(), req.getJdTitle()), null, null);
        } catch (Exception e) {
            log.warn("Failed to log audit for interview {}: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    @Transactional
    public Interview abandonInterview(String id, AbandonInterviewRequest req) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));
        if (req.getTranscriptJson() != null) interview.setTranscriptJson(req.getTranscriptJson());
        interview.setStatus(InterviewStatus.WITHDRAWN);
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

        // Broadcast to all branch admins and recruiters so the whole team can review
        try {
            Map<String, String> notif = new java.util.HashMap<>();
            notif.put("interviewId", saved.getId());
            notif.put("branch", saved.getBranch() != null ? saved.getBranch() : com.benchreadiness.interview.branch.BranchAccess.defaultBranch());
            notif.put("candidateName", engineerName);
            notif.put("status", InterviewStatus.WITHDRAWN.name());
            notif.put("proposedVerdict", ReadinessVerdict.WITHDRAWN.name());
            notif.put("reason", req.getReason() != null ? req.getReason() : "not_prepared");
            observerServiceClient.notifyInterviewCompleted(notif);
        } catch (Exception e) {
            log.warn("Failed to send withdrawal notification for interview {}: {}", saved.getId(), e.getMessage());
        }

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
            
            // Send cancellation email only when the interview was scheduled but not yet attended
            if (engineer != null && engineer.getEmail() != null
                    && interview.getStatus() == InterviewStatus.SCHEDULED) {
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
            
            // Delete recording file if present
            if (interview.getRecordingPath() != null) {
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(interview.getRecordingPath()));
                } catch (Exception e) {
                    log.warn("Could not delete recording file for interview {}: {}", id, e.getMessage());
                }
            }

            // Delete the interview first (it holds FKs to plan and JD)
            interviewRepository.deleteById(id);

            // Then delete orphaned plan and JD
            if (planId != null && planRepository.existsById(planId)) {
                planRepository.deleteById(planId);
            }
            if (jdId != null && jdRepository.existsById(jdId)) {
                jdRepository.deleteById(jdId);
            }

            // Delete engineer record if they have no remaining interviews
            if (engineerId != null && interviewRepository.findByEngineerId(engineerId).isEmpty()) {
                engineerRepository.deleteById(engineerId);
                log.info("Deleted orphaned engineer {} after last interview removed", engineerId);
            }

            log.info("Successfully deleted interview {} and related entities", id);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete interview {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete interview: " + e.getMessage());
        }
    }

    public Optional<Interview> findById(String id) {
        return interviewRepository.findById(id).map(proctoringSupport::enrichInterview);
    }

    public Optional<Interview> findByIdForRole(String id, String userId, String userRole) {
        return findById(id).filter(interview -> {
            Map<String, String> candidateBranches = candidateBranchLookup.byEngineerIds(
                    Set.of(interview.getEngineerId()));
            return InterviewBranchFilter.canAccess(interview, userId, userRole, candidateBranches);
        });
    }

    public Optional<Interview> findByIdForCandidate(String id, String userEmail) {
        return findById(id).filter(interview -> {
            boolean ownsInterview = engineerRepository.findByEmail(userEmail)
                .map(e -> e.getId().equals(interview.getEngineerId()))
                .orElse(false);
            if (!ownsInterview) return false;

            Instant now = Instant.now();
            if (interview.getExpiresAt() != null && now.isAfter(interview.getExpiresAt())) {
                throw new com.benchreadiness.interview.exception.InterviewExpiredException(
                        "This interview link has expired.");
            }
            if (interview.getScheduledAt() != null && now.isBefore(interview.getScheduledAt())) {
                throw new com.benchreadiness.interview.exception.InterviewNotYetAvailableException(
                        "This interview is not yet available. Please come back at the scheduled time.");
            }
            return true;
        });
    }

    @Transactional
    public Interview saveInterview(Interview interview) {
        return interviewRepository.save(interview);
    }

    @Transactional
    public Interview saveCheckpoint(String id, String checkpointJson) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));
        interview.setCheckpointJson(checkpointJson);
        return interviewRepository.save(interview);
    }

    private static final int MAX_INTERVIEWS_PAGE = 1000;

    public List<Interview> findAll() {
        return interviewRepository.findAllPaged(PageRequest.of(0, MAX_INTERVIEWS_PAGE));
    }

    public List<Interview> findAllForRole(String userId, String userRole) {
        List<Interview> all = findAll();
        return InterviewBranchFilter.filterForRole(all, userId, userRole, candidateBranchLookup.forInterviews(all));
    }

    public List<com.benchreadiness.interview.dto.InterviewSummaryDto> getSummaries(String userId, String userRole) {
        List<Interview> all = interviewRepository.findAll();
        return mapSummaries(InterviewBranchFilter.filterForRole(
                all, userId, userRole, candidateBranchLookup.forInterviews(all)));
    }

    public List<com.benchreadiness.interview.dto.InterviewSummaryDto> getSummaries() {
        return mapSummaries(interviewRepository.findAllPaged(PageRequest.of(0, MAX_INTERVIEWS_PAGE)));
    }

    private List<com.benchreadiness.interview.dto.InterviewSummaryDto> mapSummaries(List<Interview> interviews) {
        if (interviews.isEmpty()) return List.of();

        // Batch-load engineers and JDs to avoid N+1 queries
        Set<String> engineerIds = new HashSet<>();
        Set<String> jdIds = new HashSet<>();
        for (Interview i : interviews) {
            if (i.getEngineerId() != null) engineerIds.add(i.getEngineerId());
            if (i.getJdId() != null) jdIds.add(i.getJdId());
        }
        Map<String, Engineer> engineerMap = new HashMap<>();
        engineerRepository.findAllById(engineerIds).forEach(e -> engineerMap.put(e.getId(), e));
        Map<String, JobDescription> jdMap = new HashMap<>();
        jdRepository.findAllById(jdIds).forEach(j -> jdMap.put(j.getId(), j));

        return interviews.stream().map(interview -> {
            Engineer engineer = engineerMap.get(interview.getEngineerId());
            JobDescription jd = jdMap.get(interview.getJdId());
            String candidateName = engineer != null && engineer.getName() != null && !engineer.getName().isBlank()
                    ? engineer.getName()
                    : (engineer != null ? engineer.getEmail() : "Unknown");
            String candidateEmail = engineer != null ? engineer.getEmail() : "";
            String jdTitle = jd != null ? jd.getTitle() : "";
            String proposedVerdict = interview.getProposedVerdict() != null ? interview.getProposedVerdict().name() : null;
            String finalVerdict = interview.getFinalVerdict() != null ? interview.getFinalVerdict().name() : null;
            com.benchreadiness.interview.dto.InterviewSummaryDto dto = new com.benchreadiness.interview.dto.InterviewSummaryDto(
                    interview.getId(), interview.getStatus().name(), proposedVerdict, finalVerdict,
                    candidateName, candidateEmail, jdTitle,
                    interview.getCreatedAt() != null ? interview.getCreatedAt().toString() : "",
                    interview.getInterviewMode() != null ? interview.getInterviewMode().name() : "SCREENING"
            );
            dto.setEndedAt(interview.getEndedAt() != null ? interview.getEndedAt().toString() : null);
            dto.setScheduledAt(interview.getScheduledAt() != null ? interview.getScheduledAt().toString() : null);
            dto.setExpiresAt(interview.getExpiresAt() != null ? interview.getExpiresAt().toString() : null);
            return dto;
        }).toList();
    }

    public List<com.benchreadiness.interview.dto.InterviewSummaryDto> getTodaysSummaries(String userId, String userRole) {
        java.time.LocalDate today = java.time.LocalDate.now();
        Instant startOfDay = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        List<Interview> todayInterviews = interviewRepository.findCreatedToday(startOfDay, endOfDay);
        return mapSummaries(InterviewBranchFilter.filterForRole(
                todayInterviews, userId, userRole, candidateBranchLookup.forInterviews(todayInterviews)));
    }

    public List<com.benchreadiness.interview.dto.InterviewSummaryDto> getTodaysSummaries() {
        java.time.LocalDate today = java.time.LocalDate.now();
        Instant startOfDay = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        return mapSummaries(interviewRepository.findCreatedToday(startOfDay, endOfDay));
    }

    public List<Interview> findByEmail(String email) {
        return engineerRepository.findByEmail(email)
                .map(e -> interviewRepository.findByEngineerId(e.getId()))
                .orElse(List.of());
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
        interview.setCheckpointJson(null);
        if (req.getStatus() != null) {
            try {
                InterviewStatus requested = InterviewStatus.valueOf(req.getStatus());
                if (requested == InterviewStatus.COMPLETED || requested == InterviewStatus.REVIEW_PENDING) {
                    interview.setStatus(requested);
                }
            } catch (Exception ignored) {}
        }
        if (req.getProposedVerdict() != null) {
            try { interview.setProposedVerdict(ReadinessVerdict.valueOf(req.getProposedVerdict())); } catch (Exception ignored) {}
        }
        if (req.getFinalVerdict() != null) {
            try { interview.setFinalVerdict(ReadinessVerdict.valueOf(req.getFinalVerdict())); } catch (Exception ignored) {}
        }
        interview.setEndedAt(Instant.now());
        Interview saved = interviewRepository.save(interview);

        // Increment system interview count for the candidate on completion
        if (saved.getStatus() == InterviewStatus.COMPLETED) {
            try {
                Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
                if (engineer != null && engineer.getEmail() != null) {
                    authServiceClient.incrementSystemInterviewCountByEmail(engineer.getEmail());
                }
            } catch (Exception e) {
                log.warn("Failed to increment system interview count for interview {}: {}", id, e.getMessage());
            }

            // Deactivate market candidate credentials after interview completion
            try {
                Engineer eng = engineerRepository.findById(interview.getEngineerId()).orElse(null);
                if (eng != null) {
                    Map<String, Object> profile = authServiceClient.getUserByEmail(eng.getEmail());
                    if (profile != null && "MARKET".equals(profile.get("source"))) {
                        String candidateUserId = (String) profile.get("id");
                        if (candidateUserId != null) {
                            authServiceClient.setUserActive(candidateUserId, Map.of("active", false));
                            log.info("Deactivated market candidate {} after completing interview {}", candidateUserId, id);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not deactivate market candidate after interview {}: {}", id, e.getMessage());
            }
        }
        
        // Log audit trail
        Engineer engineer = engineerRepository.findById(saved.getEngineerId()).orElse(null);
        String engineerName = engineer != null ? engineer.getName() : "Unknown";
        logAudit(saved.getEngineerId(), engineerName, "CANDIDATE", "INTERVIEW_COMPLETED", saved.getId(),
            String.format("Status: %s, Verdict: %s", saved.getStatus(),
                saved.getProposedVerdict() != null ? saved.getProposedVerdict() : "N/A"),
            null, null);

        // Notify all branch admins and recruiters to review
        if (saved.getStatus() == InterviewStatus.COMPLETED || saved.getStatus() == InterviewStatus.REVIEW_PENDING) {
            try {
                Map<String, String> notif = new java.util.HashMap<>();
                notif.put("interviewId", saved.getId());
                notif.put("branch", saved.getBranch() != null ? saved.getBranch() : com.benchreadiness.interview.branch.BranchAccess.defaultBranch());
                notif.put("candidateName", engineerName);
                notif.put("status", saved.getStatus().name());
                notif.put("proposedVerdict", saved.getProposedVerdict() != null ? saved.getProposedVerdict().name() : "UNKNOWN");
                observerServiceClient.notifyInterviewCompleted(notif);
            } catch (Exception e) {
                log.warn("Failed to send completion notification for interview {}: {}", saved.getId(), e.getMessage());
            }
        }

        return saved;
    }

    @Transactional
    public Interview updateInterview(String id, Map<String, String> updates) {
        log.info("Updating interview {} with updates: {}", id, updates);
        
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));

        // Prevent backward status transitions from terminal SIGNED_OFF state
        if (interview.getStatus() == InterviewStatus.SIGNED_OFF
                && updates.containsKey("status")
                && !InterviewStatus.SIGNED_OFF.name().equals(updates.get("status"))) {
            throw new IllegalStateException("Cannot change status of a SIGNED_OFF interview");
        }

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

    @Transactional
    public Interview startLiveInterview(String id) {
        Interview interview = interviewRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));
        if (interview.getStatus() == InterviewStatus.IN_PROGRESS) {
            return interview; // Idempotent — already started by a concurrent request
        }
        if (interview.getStatus() == InterviewStatus.DRAFT || interview.getStatus() == InterviewStatus.SCHEDULED) {
            interview.setStatus(InterviewStatus.IN_PROGRESS);
        }
        if (interview.getStartedAt() == null) {
            interview.setStartedAt(Instant.now());
        }
        return interviewRepository.save(interview);
    }

    @Transactional
    public Interview updateAssessmentStatus(String id, String status, String error, String resultJson, String runId) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));
        if (status != null && !status.isBlank()) {
            interview.setAssessmentStatus(status);
            if ("PROCESSING".equalsIgnoreCase(status)) {
                interview.setAssessmentResultJson(null);
                interview.setAssessmentError(null);
            }
        }
        if (error != null) {
            interview.setAssessmentError(error.isBlank() ? null : error);
        }
        if (resultJson != null) {
            interview.setAssessmentResultJson(resultJson.isBlank() ? null : resultJson);
        }
        if (runId != null) {
            interview.setAssessmentRunId(runId.isBlank() ? null : runId);
        }
        return interviewRepository.save(interview);
    }

    private String filterProgrammingFromQuestionBankJson(String questionBankQuestionsJson) throws Exception {
        com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(questionBankQuestionsJson);
        if (!arr.isArray()) {
            return questionBankQuestionsJson;
        }
        com.fasterxml.jackson.databind.node.ArrayNode filtered = objectMapper.createArrayNode();
        for (com.fasterxml.jackson.databind.JsonNode q : arr) {
            String type = q.path("questionType").asText("TECHNICAL");
            if (!"CODING".equalsIgnoreCase(type)) {
                filtered.add(q);
            }
        }
        if (filtered.isEmpty()) {
            throw new IllegalArgumentException(
                "All selected question-bank questions are of type CODING, but this interview excludes programming questions. " +
                "Either enable programming questions or select non-coding questions from the question bank.");
        }
        return objectMapper.writeValueAsString(filtered);
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
            auditLog.put("ipAddress", resolveClientIp());
            complianceServiceClient.recordAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage());
        }
    }

    private String resolveClientIp() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                (org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "internal";
            jakarta.servlet.http.HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isBlank()) return ip.trim();
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
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

    @Transactional
    public Interview editInterview(String id, com.benchreadiness.interview.dto.UpdateInterviewRequest req) throws Exception {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));

        if (interview.getStatus() != InterviewStatus.DRAFT
                && interview.getStatus() != InterviewStatus.SCHEDULED
                && interview.getStatus() != InterviewStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Interview can only be edited when in DRAFT, SCHEDULED, or EXPIRED state, current: " + interview.getStatus());
        }

        if (req.getJdTitle() != null && !req.getJdTitle().isBlank()) {
            jdRepository.findById(interview.getJdId()).ifPresent(jd -> {
                jd.setTitle(req.getJdTitle().trim());
                if (req.getJdText() != null) jd.setText(req.getJdText());
                jdRepository.save(jd);
            });
        }
        if (req.getInterviewMode() != null) interview.setInterviewMode(req.getInterviewMode());
        if (req.getCustomDurationMinutes() != null) interview.setCustomDurationMinutes(req.getCustomDurationMinutes());
        if (req.getRoundName() != null) interview.setRoundName(req.getRoundName());
        if (req.getIncludeProgrammingQuestions() != null) interview.setIncludeProgrammingQuestions(req.getIncludeProgrammingQuestions());
        if (req.getScheduledAt() != null) interview.setScheduledAt(req.getScheduledAt());
        if (req.getExpiresAt() != null) {
            interview.setExpiresAt(req.getExpiresAt());
            // Re-open an expired interview when admin pushes the expiry into the future
            if (interview.getStatus() == InterviewStatus.EXPIRED
                    && req.getExpiresAt().isAfter(Instant.now())) {
                interview.setStatus(InterviewStatus.SCHEDULED);
                log.info("Re-opened expired interview {} — new expiresAt: {}", id, req.getExpiresAt());
            }
        }

        if (req.getCustomQuestions() != null) {
            try {
                interview.setCustomQuestionsJson(objectMapper.writeValueAsString(req.getCustomQuestions()));
            } catch (Exception e) {
                log.warn("Failed to serialize custom questions on edit: {}", e.getMessage());
            }
        }
        if (req.getSelectedQuestionIds() != null) {
            interview.setUsedQuestionIds("");
            // Re-resolve question bank questions
            try {
                Set<String> selectedIdSet = java.util.Arrays.stream(req.getSelectedQuestionIds().split(","))
                        .map(String::trim).filter(s -> !s.isBlank())
                        .collect(java.util.stream.Collectors.toSet());
                com.fasterxml.jackson.databind.JsonNode resp = questionBankClient.fetchQuestionsForInterview(null, null, 200);
                if (resp != null && resp.has("data") && resp.get("data").isArray()) {
                    List<com.fasterxml.jackson.databind.JsonNode> filtered = new ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode q : resp.get("data")) {
                        if (selectedIdSet.contains(q.path("id").asText())) filtered.add(q);
                    }
                    if (!filtered.isEmpty()) {
                        interview.setQuestionBankQuestionsJson(objectMapper.writeValueAsString(filtered));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to re-resolve question bank questions on edit: {}", e.getMessage());
            }
        }

        // If re-scheduling (scheduledAt or expiresAt changed), re-activate market candidate + notify candidate
        if (req.getScheduledAt() != null || req.getExpiresAt() != null) {
            Engineer engineer = null;
            try {
                engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
                if (engineer != null) {
                    Map<String, Object> profile = authServiceClient.getUserByEmail(engineer.getEmail());
                    if (profile != null && "MARKET".equals(profile.get("source"))) {
                        String candidateUserId = (String) profile.get("id");
                        if (candidateUserId != null) {
                            authServiceClient.setUserActive(candidateUserId, Map.of("active", true));
                            log.info("Re-activated market candidate {} on interview edit {}", candidateUserId, id);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not re-activate market candidate on interview edit {}: {}", id, e.getMessage());
            }

            // Send rescheduled notification to candidate
            try {
                if (engineer != null && engineer.getEmail() != null) {
                    java.time.format.DateTimeFormatter emailFmt =
                        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' hh:mm a 'UTC'")
                            .withZone(java.time.ZoneOffset.UTC);
                    Instant effectiveScheduledAt = req.getScheduledAt() != null ? req.getScheduledAt() : interview.getScheduledAt();
                    Instant effectiveExpiresAt   = req.getExpiresAt()   != null ? req.getExpiresAt()   : interview.getExpiresAt();
                    Map<String, String> notifyBody = new java.util.LinkedHashMap<>();
                    notifyBody.put("email", engineer.getEmail());
                    notifyBody.put("name", engineer.getName() != null ? engineer.getName() : "");
                    notifyBody.put("scheduledAt", effectiveScheduledAt != null ? emailFmt.format(effectiveScheduledAt) : "—");
                    notifyBody.put("expiresAt", effectiveExpiresAt != null ? emailFmt.format(effectiveExpiresAt) : null);
                    authServiceClient.notifyInterviewScheduled(notifyBody);
                    log.info("Rescheduled notification sent to {} for interview {}", engineer.getEmail(), id);
                }
            } catch (Exception e) {
                log.warn("Failed to send rescheduled email for interview {}: {}", id, e.getMessage());
            }
        }

        return interviewRepository.save(interview);
    }

    /**
     * Creates interviews for multiple candidates concurrently.
     * Each candidate gets its own CompletableFuture on the bulkInterviewExecutor.
     * One failure does NOT stop the others.
     * Returns HTTP 207 Multi-Status via the controller.
     */
    public BulkCreateInterviewResult createBulkInterviews(
            BulkCreateInterviewRequest req,
            String createdByUserId,
            String userRole,
            com.benchreadiness.interview.service.BranchInterviewValidator branchValidator) {

        List<CompletableFuture<BulkCreateInterviewResult.CandidateResult>> futures =
            req.getCandidates().stream().map(candidate ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        // Build a per-candidate CreateInterviewRequest from shared config
                        CreateInterviewRequest single = new CreateInterviewRequest();
                        single.setJdTitle(req.getJdTitle());
                        single.setJdText(req.getJdText());
                        single.setFocusAreas(req.getFocusAreas());
                        single.setInterviewMode(req.getInterviewMode());
                        single.setCustomDurationMinutes(req.getCustomDurationMinutes());
                        single.setIncludeProgrammingQuestions(req.getIncludeProgrammingQuestions());
                        single.setScheduledAt(req.getScheduledAt());
                        single.setExpiresAt(req.getExpiresAt());
                        single.setRoundName(req.getRoundName());
                        single.setEngineerEmail(candidate.getEngineerEmail());
                        single.setEngineerName(candidate.getEngineerName());
                        single.setResumeSummary(candidate.getResumeSummary());
                        single.setClientId(candidate.getClientId());

                        // Reuse the same branch validation logic as single-interview creation
                        String branch = branchValidator.validateAndResolveBranch(single, createdByUserId, userRole);

                        java.util.UUID clientUuid = null;
                        if (candidate.getClientId() != null && !candidate.getClientId().isBlank()) {
                            try { clientUuid = java.util.UUID.fromString(candidate.getClientId()); }
                            catch (IllegalArgumentException ignored) { }
                        }

                        Interview created = createInterview(single, createdByUserId, branch, clientUuid);
                        log.info("[Bulk] Created interview {} for {}", created.getId(), candidate.getEngineerEmail());
                        return BulkCreateInterviewResult.CandidateResult
                                .ok(candidate.getEngineerEmail(), created.getId());
                    } catch (Exception e) {
                        log.warn("[Bulk] Failed to create interview for {}: {}",
                                candidate.getEngineerEmail(), e.getMessage());
                        return BulkCreateInterviewResult.CandidateResult
                                .failed(candidate.getEngineerEmail(), e.getMessage());
                    }
                }, bulkInterviewExecutor)
            ).collect(Collectors.toList());

        // Wait for all — max 120s (rubric generation per candidate can take 30-60s each, but they run in parallel)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(120, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("[Bulk] Timed out waiting for all candidates — collecting partial results");
        } catch (Exception e) {
            log.warn("[Bulk] Error waiting for futures: {}", e.getMessage());
        }

        List<BulkCreateInterviewResult.CandidateResult> results = futures.stream().map(f -> {
            if (f.isDone() && !f.isCompletedExceptionally()) {
                try { return f.get(); } catch (Exception ignored) { }
            }
            return BulkCreateInterviewResult.CandidateResult.failed("unknown", "Timed out or internal error");
        }).collect(Collectors.toList());

        return new BulkCreateInterviewResult(results);
    }
}
