package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.entity.Engineer;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.repository.EngineerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class InterviewProctoringSupport {

    private static final Duration SETTINGS_CACHE_TTL = Duration.ofSeconds(30);

    private final EngineerRepository engineerRepository;
    private final AuthServiceClient authServiceClient;
    private final ObjectMapper objectMapper;

    private volatile Map<String, Boolean> cachedSettings = Map.of();
    private volatile Instant settingsLoadedAt = Instant.EPOCH;

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
        String freshMode = source != null ? resolveProctoringMode(source) : null;
        if (freshMode != null) {
            interview.setProctoringMode(freshMode);
        } else if (interview.getProctoringMode() == null) {
            // Fall back to "light" only if no persisted value exists
            interview.setProctoringMode("light");
        }
        // If freshMode is null but a persisted value exists, keep the persisted value as-is
        return interview;
    }

    public String resolveCandidateSource(Interview interview) {
        if (interview == null || interview.getEngineerId() == null) {
            return null;
        }
        Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
        return resolveCandidateSource(engineer);
    }

    public String resolveCandidateSource(Engineer engineer) {
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
        return isVideoProctoringEnabledForSource(candidateSource) ? "video" : "light";
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

    private boolean isVideoProctoringEnabledForSource(String candidateSource) {
        if (candidateSource == null || candidateSource.isBlank()) {
            return false;
        }
        String normalized = candidateSource.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        Map<String, Boolean> settings = loadProctoringSettings();
        Boolean enabled = settings.get(normalized);
        if (enabled != null) {
            return enabled;
        }
        return "MARKET".equals(normalized);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Boolean> loadProctoringSettings() {
        Instant now = Instant.now();
        if (!cachedSettings.isEmpty() && Duration.between(settingsLoadedAt, now).compareTo(SETTINGS_CACHE_TTL) < 0) {
            return cachedSettings;
        }

        try {
            Map<String, Object> response = authServiceClient.getProctoringSettings();
            Object settingsObj = response != null ? response.get("settings") : null;
            if (settingsObj instanceof Map<?, ?> rawSettings) {
                Map<String, Boolean> parsed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawSettings.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    String key = entry.getKey().toString().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
                    Object value = entry.getValue();
                    if (value instanceof Boolean b) {
                        parsed.put(key, b);
                    } else if (value instanceof String s) {
                        parsed.put(key, Boolean.parseBoolean(s));
                    }
                }
                if (!parsed.isEmpty()) {
                    cachedSettings = Map.copyOf(parsed);
                    settingsLoadedAt = now;
                    return cachedSettings;
                }
            }
        } catch (Exception ignored) {
            // fall back to defaults below
        }

        Map<String, Boolean> defaults = Map.of(
                "BENCH", false,
                "B2B", false,
                "MARKET", true
        );
        cachedSettings = defaults;
        settingsLoadedAt = now;
        return defaults;
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
