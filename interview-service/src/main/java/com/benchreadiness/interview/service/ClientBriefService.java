package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.client.AiServiceClient;
import com.benchreadiness.interview.client.ComplianceServiceClient;
import com.benchreadiness.interview.dto.ClientBriefDto;
import com.benchreadiness.interview.entity.Client;
import com.benchreadiness.interview.entity.Engineer;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.InterviewPlan;
import com.benchreadiness.interview.entity.InterviewQuestion;
import com.benchreadiness.interview.entity.JobDescription;
import com.benchreadiness.interview.repository.ClientRepository;
import com.benchreadiness.interview.repository.EngineerRepository;
import com.benchreadiness.interview.repository.InterviewPlanRepository;
import com.benchreadiness.interview.repository.InterviewQuestionRepository;
import com.benchreadiness.interview.repository.InterviewRepository;
import com.benchreadiness.interview.repository.JobDescriptionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClientBriefService {

    private static final Logger log = LoggerFactory.getLogger(ClientBriefService.class);

    private final InterviewRepository interviewRepository;
    private final EngineerRepository engineerRepository;
    private final JobDescriptionRepository jdRepository;
    private final ClientRepository clientRepository;
    private final InterviewPlanRepository planRepository;
    private final InterviewQuestionRepository questionRepository;
    private final ComplianceServiceClient complianceServiceClient;
    private final AuthServiceClient authServiceClient;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    public ClientBriefService(InterviewRepository interviewRepository,
                              EngineerRepository engineerRepository,
                              JobDescriptionRepository jdRepository,
                              ClientRepository clientRepository,
                              InterviewPlanRepository planRepository,
                              InterviewQuestionRepository questionRepository,
                              ComplianceServiceClient complianceServiceClient,
                              AuthServiceClient authServiceClient,
                              AiServiceClient aiServiceClient,
                              ObjectMapper objectMapper) {
        this.interviewRepository = interviewRepository;
        this.engineerRepository = engineerRepository;
        this.jdRepository = jdRepository;
        this.clientRepository = clientRepository;
        this.planRepository = planRepository;
        this.questionRepository = questionRepository;
        this.complianceServiceClient = complianceServiceClient;
        this.authServiceClient = authServiceClient;
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
    }

    public ClientBriefResponse getClientBrief(String interviewId, String userId) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new IllegalArgumentException("Interview not found"));

        ClientBriefContext context = buildContext(interview, userId);

        if (interview.getClientBriefJson() != null && !interview.getClientBriefJson().isBlank()) {
            try {
                Map<String, Object> stored = objectMapper.readValue(
                    interview.getClientBriefJson(), new TypeReference<>() {});
                ClientBriefDto brief = ClientBriefDto.fromMap(stored);
                mergeContextIntoBrief(brief, context);
                brief.setSaved(true);
                return new ClientBriefResponse(brief, context, true);
            } catch (Exception e) {
                log.warn("Failed to parse saved client brief for {}: {}", interviewId, e.getMessage());
            }
        }

        return new ClientBriefResponse(null, context, false);
    }

    public ClientBriefResponse generateClientBrief(String interviewId, String userId) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new IllegalArgumentException("Interview not found"));

        String assessmentJson = resolveAssessmentJson(interview, userId);
        if (assessmentJson == null || assessmentJson.isBlank()) {
            throw new IllegalArgumentException(
                "No AI assessment found. Run assessment before generating a client brief.");
        }

        ClientBriefContext context = buildContext(interview, userId);
        JobDescription jd = jdRepository.findById(interview.getJdId()).orElse(null);
        String jdTitle = jd != null ? jd.getTitle() : "Position";
        String jdText = jd != null && jd.getText() != null ? jd.getText() : "";
        String rubricJson = resolveRubricJson(interview);

        Map<String, Object> aiPayload = new LinkedHashMap<>();
        aiPayload.put("interviewId", interviewId);
        aiPayload.put("jdTitle", jdTitle);
        aiPayload.put("jdText", jdText);
        aiPayload.put("assessmentJson", assessmentJson);
        aiPayload.put("rubricJson", rubricJson);
        aiPayload.put("candidateName", context.candidateName());
        aiPayload.put("candidateEmail", context.candidateEmail());
        aiPayload.put("clientName", context.clientName());
        aiPayload.put("roundName", context.roundName());
        aiPayload.put("seniorityBand", context.seniorityBand());
        aiPayload.put("interviewDate", context.interviewDate());
        aiPayload.put("totalMinutes", context.totalMinutes());
        aiPayload.put("playbackUrl", context.playbackUrl());
        aiPayload.put("resumeUrl", context.resumeUrl());
        aiPayload.put("reviewerName", context.reviewerName());
        aiPayload.put("reviewerYoe", context.reviewerYoe());
        aiPayload.put("reviewerCompany", context.reviewerCompany());
        aiPayload.put("reviewerSkills", context.reviewerSkills());
        aiPayload.put("questions", buildQuestionPayload(interviewId));
        aiPayload.put("transcriptJson", interview.getTranscriptJson());

        Map<String, Object> generated = aiServiceClient.generateClientBrief(aiPayload, userId);
        if (generated == null || generated.isEmpty()) {
            throw new IllegalArgumentException("AI returned an empty client brief");
        }
        if (generated.get("error") != null) {
            throw new IllegalArgumentException(String.valueOf(generated.get("error")));
        }

        ClientBriefDto brief = ClientBriefDto.fromMap(generated);
        mergeContextIntoBrief(brief, context);
        brief.setSaved(false);
        brief.setSource(brief.getSource() != null && !brief.getSource().isBlank() ? brief.getSource() : "ai");
        return new ClientBriefResponse(brief, context, false);
    }

    @Transactional
    public ClientBriefResponse saveClientBrief(String interviewId, ClientBriefDto brief,
                                               String userId, String userName) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new IllegalArgumentException("Interview not found"));

        try {
            ClientBriefContext context = buildContext(interview, userId);
            mergeContextIntoBrief(brief, context);
            Map<String, Object> payload = toStorageMap(brief, userId, userName);
            interview.setClientBriefJson(objectMapper.writeValueAsString(payload));
            interviewRepository.save(interview);

            ClientBriefDto saved = ClientBriefDto.fromMap(payload);
            saved.setSaved(true);
            saved.setLastEditedByUserId(userId);
            saved.setLastEditedByName(userName);
            saved.setLastEditedAt(Instant.now());
            saved.setSource("manual");
            return new ClientBriefResponse(saved, context, true);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to save client brief: " + e.getMessage());
        }
    }

    public ClientBriefPdfContext getPdfContext(String interviewId, String userId) {
        ClientBriefResponse response = getClientBrief(interviewId, userId);
        if (response.brief() == null || !response.brief().isSaved()) {
            throw new IllegalArgumentException(
                "Save the client brief before downloading. Review and edit the draft, then click Save.");
        }
        return new ClientBriefPdfContext(response.brief(), response.context());
    }

    private void mergeContextIntoBrief(ClientBriefDto brief, ClientBriefContext context) {
        if (brief.getHeader() == null) {
            brief.setHeader(new ClientBriefDto.BriefHeader());
        }
        ClientBriefDto.BriefHeader header = brief.getHeader();
        header.setCandidateName(context.candidateName());
        header.setCandidateEmail(context.candidateEmail());
        header.setPositionTitle(context.jdTitle());
        header.setClientName(context.clientName());
        header.setRoundName(context.roundName());
        header.setSeniorityBand(context.seniorityBand());
        header.setInterviewDate(context.interviewDate());
        header.setTotalMinutes(context.totalMinutes());
        header.setPlaybackUrl(context.playbackUrl());
        header.setResumeUrl(context.resumeUrl());

        ClientBriefDto.InterviewerInfo reviewer = header.getReviewer();
        if (reviewer == null) reviewer = new ClientBriefDto.InterviewerInfo();
        reviewer.setName(context.reviewerName());
        reviewer.setYearsExperience(context.reviewerYoe());
        reviewer.setCompany(context.reviewerCompany());
        reviewer.setVerifiedSkills(context.reviewerSkills());
        header.setReviewer(reviewer);
    }

    private List<Map<String, Object>> buildQuestionPayload(String interviewId) {
        List<InterviewQuestion> questions = questionRepository.findByInterviewIdOrderBySlotNumberAsc(interviewId);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("number", question.getSlotNumber());
            item.put("text", question.getQuestionText());
            item.put("answer", question.getCandidateAnswer());
            item.put("difficulty", question.getDifficultyLevel() != null ? question.getDifficultyLevel() : "MEDIUM");
            item.put("type", question.getQuestionType() != null ? question.getQuestionType() : "TECHNICAL");
            payload.add(item);
        }
        return payload;
    }

    private String resolveRubricJson(Interview interview) {
        if (interview.getPlanId() == null) return null;
        return planRepository.findById(interview.getPlanId())
            .map(InterviewPlan::getRubricJson)
            .orElse(null);
    }

    private String resolveAssessmentJson(Interview interview, String userId) {
        String json = interview.getAssessmentResultJson();
        if (json != null && !json.isBlank()) {
            return json;
        }
        try {
            Map<String, Object> compliance = complianceServiceClient.getAssessmentResponse(
                interview.getId(), userId);
            if (compliance != null && compliance.get("assessmentJson") != null) {
                return compliance.get("assessmentJson").toString();
            }
        } catch (Exception e) {
            log.warn("Compliance assessment fetch failed for {}: {}", interview.getId(), e.getMessage());
        }
        return extractAssessmentFromTranscript(interview.getTranscriptJson());
    }

    @SuppressWarnings("unchecked")
    private String extractAssessmentFromTranscript(String transcriptJson) {
        if (transcriptJson == null || transcriptJson.isBlank()) return null;
        try {
            Map<String, Object> doc = objectMapper.readValue(transcriptJson, Map.class);
            Object meta = doc.get("meta");
            if (meta instanceof Map<?, ?> metaMap) {
                Object assessment = metaMap.get("assessment");
                if (assessment != null) {
                    return assessment instanceof String s ? s : objectMapper.writeValueAsString(assessment);
                }
            }
        } catch (Exception e) {
            log.debug("No assessment in transcript for interview: {}", e.getMessage());
        }
        return null;
    }

    private Map<String, Object> toStorageMap(ClientBriefDto brief, String userId, String userName) {
        Map<String, Object> map = brief.toMap();
        map.put("source", "manual");
        map.put("saved", true);
        map.put("lastEditedByUserId", userId);
        map.put("lastEditedByName", userName);
        map.put("lastEditedAt", Instant.now().toString());
        return map;
    }

    private ClientBriefContext buildContext(Interview interview, String userId) {
        Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
        JobDescription jd = jdRepository.findById(interview.getJdId()).orElse(null);

        String candidateName = engineer != null && engineer.getName() != null ? engineer.getName() : "Candidate";
        String candidateEmail = engineer != null ? engineer.getEmail() : "";
        String jdTitle = jd != null ? jd.getTitle() : "Position";
        String interviewDate = resolveInterviewDate(interview);

        String clientName = resolveClientName(interview, jd);
        ExperienceInfo experience = resolveCandidateExperience(engineer, interview);
        Double yoeActual = experience.yoeActual();
        Double yoePortrayed = experience.yoePortrayed();
        String skillSet = experience.skillSet();

        String roundName = interview.getRoundName();
        if (roundName == null || roundName.isBlank()) {
            roundName = defaultRoundName(interview.getInterviewMode() != null
                ? interview.getInterviewMode().name() : "SCREENING");
        }

        Integer totalMinutes = resolveTotalMinutes(interview);
        Double effectiveYoe = yoePortrayed != null ? yoePortrayed : yoeActual;
        String seniorityBand = buildSeniorityBand(
            effectiveYoe, experience.level(), experience.gradeLevel(), skillSet);

        ReviewerInfo reviewer = resolveReviewer(interview);
        String candidateUserId = resolveCandidateUserId(engineer);
        String playbackUrl = interview.getRecordingPath() != null && !interview.getRecordingPath().isBlank()
            ? "/interviews/" + interview.getId() + "/recording" : "";
        String resumeUrl = candidateUserId != null
            ? "/resumes/download/" + candidateUserId : "";

        return new ClientBriefContext(
            candidateName,
            candidateEmail,
            jdTitle,
            clientName,
            interviewDate,
            interview.getInterviewMode() != null ? interview.getInterviewMode().name() : "SCREENING",
            roundName,
            seniorityBand,
            totalMinutes,
            interview.getFinalVerdict() != null ? interview.getFinalVerdict().name()
                : interview.getProposedVerdict() != null ? interview.getProposedVerdict().name() : null,
            skillSet,
            yoeActual,
            yoePortrayed,
            reviewer.name(),
            reviewer.yoe(),
            reviewer.company(),
            reviewer.skills(),
            playbackUrl,
            resumeUrl,
            interview.getSignedOffAt() != null ? interview.getSignedOffAt().toString() : null
        );
    }

    private ReviewerInfo resolveReviewer(Interview interview) {
        String reviewerUserId = interview.getSignOffByUserId();
        if (reviewerUserId == null || reviewerUserId.isBlank()) {
            reviewerUserId = interview.getCreatedByUserId();
        }
        if (reviewerUserId == null || reviewerUserId.isBlank()) {
            return new ReviewerInfo("Bench Readiness Reviewer", "", "Bench Readiness", List.of());
        }
        try {
            Map<String, Object> user = authServiceClient.getUserById(reviewerUserId);
            String name = stringVal(user.get("name"));
            if (name.isBlank()) name = stringVal(user.get("fullName"));
            if (name.isBlank()) name = "Reviewer";

            String yoe = "";
            if (user.get("yoeActual") != null) {
                yoe = user.get("yoeActual").toString();
            } else if (user.get("yearsOfExperience") != null) {
                yoe = user.get("yearsOfExperience").toString();
            }

            String company = stringVal(user.get("company"));
            if (company.isBlank()) company = "Bench Readiness";

            List<String> skills = new ArrayList<>();
            Object skillObj = user.get("skillSet");
            if (skillObj != null && !skillObj.toString().isBlank()) {
                skills.add(formatSkillLabel(skillObj.toString()));
            }
            Object primarySkills = user.get("primarySkills");
            if (primarySkills instanceof List<?> list) {
                list.stream().map(String::valueOf).map(this::formatSkillLabel).forEach(skills::add);
            }

            return new ReviewerInfo(name, yoe, company, skills.stream().distinct().limit(8).toList());
        } catch (Exception e) {
            log.debug("Could not resolve reviewer profile for {}: {}", reviewerUserId, e.getMessage());
            return new ReviewerInfo("Bench Readiness Reviewer", "", "Bench Readiness", List.of());
        }
    }

    private String formatSkillLabel(String skill) {
        return skill.replace('_', ' ');
    }

    private String defaultRoundName(String interviewMode) {
        return switch (interviewMode) {
            case "SCREENING" -> "Screening";
            case "L1" -> "Level 1";
            case "L2" -> "Level 2";
            case "L3" -> "Hands-On";
            case "L4" -> "Advanced Hands-On";
            default -> interviewMode.replace('_', ' ');
        };
    }

    private Integer resolveTotalMinutes(Interview interview) {
        if (interview.getStartedAt() != null && interview.getEndedAt() != null) {
            long minutes = Duration.between(interview.getStartedAt(), interview.getEndedAt()).toMinutes();
            if (minutes > 0 && minutes <= 480) {
                return (int) minutes;
            }
        }
        if (interview.getCustomDurationMinutes() != null && interview.getCustomDurationMinutes() > 0) {
            int custom = interview.getCustomDurationMinutes();
            if (custom <= 480) {
                return custom;
            }
        }
        return null;
    }

    private String resolveInterviewDate(Interview interview) {
        Instant when = interview.getStartedAt() != null ? interview.getStartedAt()
            : interview.getScheduledAt() != null ? interview.getScheduledAt() : interview.getCreatedAt();
        if (when == null) return "";
        return DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
            .withZone(ZoneId.systemDefault())
            .format(when);
    }

    private String resolveClientName(Interview interview, JobDescription jd) {
        if (interview.getClientId() != null) {
            String fromId = clientRepository.findById(interview.getClientId())
                .map(Client::getClientName).orElse("");
            if (!fromId.isBlank()) {
                return fromId;
            }
        }

        if (jd != null && jd.getTitle() != null && !jd.getTitle().isBlank()) {
            String jdTitle = jd.getTitle().trim();
            String fromRole = clientRepository.findFirstByJdRoleIgnoreCase(jdTitle)
                .map(Client::getClientName).orElse("");
            if (!fromRole.isBlank()) {
                return fromRole;
            }

            String titleLower = jdTitle.toLowerCase();
            for (Client client : clientRepository.findByStatus(Client.ClientStatus.ACTIVE)) {
                String role = client.getJdRole();
                if (role == null || role.isBlank()) continue;
                String roleLower = role.toLowerCase();
                if (titleLower.contains(roleLower) || roleLower.contains(titleLower)) {
                    return client.getClientName();
                }
            }
        }

        if (jd != null && jd.getSource() != null && !jd.getSource().isBlank()
                && !"paste".equalsIgnoreCase(jd.getSource())) {
            return jd.getSource().trim();
        }
        return "";
    }

    private ExperienceInfo resolveCandidateExperience(Engineer engineer, Interview interview) {
        Double yoeActual = engineer != null && engineer.getYearsExperience() != null
            ? engineer.getYearsExperience().doubleValue() : null;
        Double yoePortrayed = null;
        String skillSet = engineer != null ? engineer.getPrimaryTrack() : null;
        String level = null;
        String gradeLevel = engineer != null ? engineer.getGradeLevel() : null;

        Map<String, Object> user = fetchCandidateUser(engineer);
        if (user != null) {
            yoeActual = firstNonNull(parseDouble(user.get("yoeActual")), yoeActual);
            yoePortrayed = parseDouble(user.get("yoePortrayed"));
            yoeActual = firstNonNull(parseDouble(user.get("yearsOfExperience")), yoeActual);
            String profileSkill = stringVal(user.get("skillSet"));
            if (!profileSkill.isBlank()) {
                skillSet = profileSkill;
            }
        }

        if (interview.getPlanId() != null) {
            ProfileEnrichment profile = enrichFromInterviewPlan(interview.getPlanId());
            yoeActual = firstNonNull(profile.yoe(), yoeActual);
            level = firstNonBlank(profile.level(), level);
            skillSet = firstNonBlank(profile.primarySkills(), skillSet);
        }

        return new ExperienceInfo(yoeActual, yoePortrayed, skillSet, level, gradeLevel);
    }

    private ProfileEnrichment enrichFromInterviewPlan(String planId) {
        if (planId == null || planId.isBlank()) {
            return ProfileEnrichment.empty();
        }
        return planRepository.findById(planId).map(plan -> {
            Double yoe = null;
            String level = null;
            String skills = null;

            if (plan.getCandidateProfileJson() != null && !plan.getCandidateProfileJson().isBlank()) {
                try {
                    Map<String, Object> profile = objectMapper.readValue(
                        plan.getCandidateProfileJson(), new TypeReference<>() {});
                    yoe = parseDouble(profile.get("yearsOfExperience"));
                    level = stringVal(profile.get("level"));
                    Object primary = profile.get("primarySkills");
                    if (primary instanceof List<?> list && !list.isEmpty()) {
                        skills = list.stream().map(String::valueOf).findFirst().orElse("");
                    }
                } catch (Exception e) {
                    log.debug("Could not parse candidateProfileJson for brief context: {}", e.getMessage());
                }
            }

            if (yoe == null && plan.getGapMapJson() != null && !plan.getGapMapJson().isBlank()) {
                try {
                    Map<String, Object> gap = objectMapper.readValue(
                        plan.getGapMapJson(), new TypeReference<>() {});
                    yoe = firstNonNull(parseYearsFromText(stringVal(gap.get("resumeSummary"))), yoe);
                } catch (Exception e) {
                    log.debug("Could not parse gapMapJson for brief context: {}", e.getMessage());
                }
            }
            return new ProfileEnrichment(yoe, level, skills);
        }).orElse(ProfileEnrichment.empty());
    }

    private String resolveCandidateUserId(Engineer engineer) {
        if (engineer == null) {
            return null;
        }
        Map<String, Object> user = fetchCandidateUser(engineer);
        if (user != null) {
            String id = stringVal(user.get("id"));
            if (!id.isBlank()) {
                return id;
            }
            id = stringVal(user.get("userId"));
            if (!id.isBlank()) {
                return id;
            }
        }
        String userId = engineer.getUserId();
        return userId != null && !userId.isBlank() ? userId : null;
    }

    private Map<String, Object> fetchCandidateUser(Engineer engineer) {
        if (engineer == null) {
            return null;
        }
        List<String> lookupKeys = new ArrayList<>();
        if (engineer.getUserId() != null && !engineer.getUserId().isBlank()) {
            lookupKeys.add(engineer.getUserId());
        }
        if (engineer.getEmail() != null && !engineer.getEmail().isBlank()
                && !lookupKeys.contains(engineer.getEmail())) {
            lookupKeys.add(engineer.getEmail());
        }
        for (String key : lookupKeys) {
            try {
                if (key.contains("@")) {
                    return authServiceClient.getUserByEmail(key);
                }
                return authServiceClient.getUserById(key);
            } catch (Exception ignored) {
                // try next lookup key
            }
        }
        return null;
    }

    private Double parseYearsFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*(?:years?|yrs?)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(text);
        if (matcher.find()) {
            return parseDouble(matcher.group(1));
        }
        return null;
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Double firstNonNull(Double primary, Double fallback) {
        return primary != null ? primary : fallback;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private String buildSeniorityBand(Double yoe, String level, String gradeLevel, String skillSet) {
        if (yoe != null) {
            int yrs = Math.max(0, (int) Math.round(yoe));
            if (yoe < 2) {
                return String.format("Junior · %d yr%s", yrs, yrs == 1 ? "" : "s");
            }
            if (yoe < 5) {
                return String.format("Mid-level · %d yrs", yrs);
            }
            if (yoe < 8) {
                return String.format("Senior · %d yrs", yrs);
            }
            return String.format("Staff+ · %d yrs", yrs);
        }
        if (level != null && !level.isBlank()) {
            return formatLevelLabel(level);
        }
        if (gradeLevel != null && !gradeLevel.isBlank()) {
            return formatGradeLevel(gradeLevel);
        }
        if (skillSet != null && !skillSet.isBlank()) {
            return formatSkillSetLabel(skillSet);
        }
        return "Experience not specified";
    }

    private String formatLevelLabel(String level) {
        return switch (level.trim().toLowerCase()) {
            case "junior" -> "Junior";
            case "mid", "middle" -> "Mid-level";
            case "senior" -> "Senior";
            case "staff", "principal", "architect" -> "Staff+";
            default -> level.substring(0, 1).toUpperCase() + level.substring(1);
        };
    }

    private String formatGradeLevel(String gradeLevel) {
        String normalized = gradeLevel.replace('_', ' ').trim();
        if (normalized.isBlank()) {
            return "Experience not specified";
        }
        return normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
    }

    private String formatSkillSetLabel(String skillSet) {
        return formatSkillLabel(skillSet) + " track";
    }

    private String stringVal(Object value) {
        return value != null ? value.toString() : "";
    }

    private record ExperienceInfo(
        Double yoeActual,
        Double yoePortrayed,
        String skillSet,
        String level,
        String gradeLevel
    ) {}

    private record ProfileEnrichment(Double yoe, String level, String primarySkills) {
        static ProfileEnrichment empty() {
            return new ProfileEnrichment(null, null, null);
        }
    }

    private record ReviewerInfo(String name, String yoe, String company, List<String> skills) {}

    public record ClientBriefResponse(ClientBriefDto brief, ClientBriefContext context, boolean hasSavedBrief) {}

    public record ClientBriefContext(
        String candidateName,
        String candidateEmail,
        String jdTitle,
        String clientName,
        String interviewDate,
        String interviewMode,
        String roundName,
        String seniorityBand,
        Integer totalMinutes,
        String verdict,
        String skillSet,
        Double yoeActual,
        Double yoePortrayed,
        String reviewerName,
        String reviewerYoe,
        String reviewerCompany,
        List<String> reviewerSkills,
        String playbackUrl,
        String resumeUrl,
        String signedOffAt
    ) {}

    public record ClientBriefPdfContext(ClientBriefDto brief, ClientBriefContext context) {}
}
