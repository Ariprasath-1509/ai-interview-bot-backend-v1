package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.entity.Engineer;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.repository.EngineerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class InterviewProctoringSupport {

    private final EngineerRepository engineerRepository;
    private final AuthServiceClient authServiceClient;
    private final ObjectMapper objectMapper;

    public InterviewProctoringSupport(EngineerRepository engineerRepository,
                                      AuthServiceClient authServiceClient,
                                      ObjectMapper objectMapper) {
        this.engineerRepository = engineerRepository;
        this.authServiceClient = authServiceClient;
        this.objectMapper = objectMapper;
    }

    public Interview enrichInterview(Interview interview) {
        String source = resolveCandidateSource(interview);
        interview.setCandidateSource(source);
        interview.setProctoringMode(resolveProctoringMode(source));
        return interview;
    }

    public String resolveCandidateSource(Interview interview) {
        if (interview == null || interview.getEngineerId() == null) {
            return null;
        }
        Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
        if (engineer == null) {
            return null;
        }
        Map<String, Object> candidate = lookupCandidate(engineer);
        if (candidate == null) {
            return null;
        }
        Object source = candidate.get("source");
        return source != null ? source.toString() : null;
    }

    public String resolveProctoringMode(String candidateSource) {
        return "MARKET".equals(candidateSource) ? "video" : "light";
    }

    public boolean requiresVideoProctoring(Interview interview) {
        return "video".equals(resolveProctoringMode(resolveCandidateSource(interview)));
    }

    public Map<String, Object> extractLightIntegrity(String transcriptJson) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tabSwitchCount", 0);
        result.put("tabSwitchViolation", false);
        result.put("fullscreenExitCount", 0);
        if (transcriptJson == null || transcriptJson.isBlank()) {
            return result;
        }
        try {
            Map<String, Object> doc = objectMapper.readValue(transcriptJson, new TypeReference<>() {});
            Object metaObj = doc.get("meta");
            if (!(metaObj instanceof Map<?, ?> meta)) {
                return result;
            }
            if (meta.get("tabSwitchCount") instanceof Number n) {
                result.put("tabSwitchCount", n.intValue());
            }
            if (meta.get("tabSwitchViolation") instanceof Boolean b) {
                result.put("tabSwitchViolation", b);
            }
            if (meta.get("fullscreenExitCount") instanceof Number n) {
                result.put("fullscreenExitCount", n.intValue());
            }
            if (meta.get("proctoringMode") != null) {
                result.put("proctoringMode", meta.get("proctoringMode"));
            }
            if (meta.get("candidateSource") != null) {
                result.put("candidateSource", meta.get("candidateSource"));
            }
        } catch (Exception ignored) {
            // best effort
        }
        return result;
    }

    private Map<String, Object> lookupCandidate(Engineer engineer) {
        if (engineer.getUserId() != null && !engineer.getUserId().isBlank()) {
            Map<String, Object> byId = safeGetUserById(engineer.getUserId());
            if (byId != null) {
                return byId;
            }
            if (engineer.getUserId().contains("@")) {
                Map<String, Object> byEmail = safeGetUserByEmail(engineer.getUserId());
                if (byEmail != null) {
                    return byEmail;
                }
            }
        }
        if (engineer.getEmail() != null && !engineer.getEmail().isBlank()) {
            return safeGetUserByEmail(engineer.getEmail());
        }
        return null;
    }

    private Map<String, Object> safeGetUserById(String userId) {
        try {
            Map<String, Object> user = authServiceClient.getUserById(userId);
            return user != null && !user.isEmpty() ? user : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> safeGetUserByEmail(String email) {
        try {
            Map<String, Object> user = authServiceClient.getUserByEmail(email);
            return user != null && !user.isEmpty() ? user : null;
        } catch (Exception e) {
            return null;
        }
    }
}
