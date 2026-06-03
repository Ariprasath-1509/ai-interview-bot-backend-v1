package com.benchreadiness.interview.service;

import com.benchreadiness.interview.dto.ProctoringEventsRequest;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.repository.InterviewRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProctoringService {

    private static final int MAX_EVENTS = 200;
    private static final int MAX_SNAPSHOTS = 20;

    private final InterviewRepository interviewRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.recording.upload-dir:${java.io.tmpdir}/br-recordings}")
    private String uploadDir;

    public ProctoringService(InterviewRepository interviewRepository, ObjectMapper objectMapper) {
        this.interviewRepository = interviewRepository;
        this.objectMapper = objectMapper;
    }

    private Path proctoringDir(String interviewId) {
        return Paths.get(uploadDir).resolve("proctoring").resolve(interviewId);
    }

    @Transactional
    public Map<String, Object> appendEvents(String interviewId, ProctoringEventsRequest req) throws IOException {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));

        Map<String, Object> existing = readJsonMap(interview.getProctoringEventsJson());
        List<Map<String, Object>> mergedEvents = mergeEvents(existing, req.getEvents());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("events", mergedEvents);
        payload.put("strikes", req.getStrikes() != null ? req.getStrikes() : existing.getOrDefault("strikes", Map.of()));
        payload.put("status", req.getStatus() != null ? req.getStatus() : existing.getOrDefault("status", "PENDING"));
        if (req.getSummary() != null) payload.put("summary", req.getSummary());
        payload.put("updatedAt", Instant.now().toString());
        payload.put("eventCount", mergedEvents.size());

        int score = req.getIntegrityScore() != null
                ? Math.max(0, Math.min(100, req.getIntegrityScore()))
                : computeScoreFromEvents(mergedEvents);
        payload.put("integrityScore", score);

        interview.setProctoringEventsJson(objectMapper.writeValueAsString(payload));
        interview.setProctoringScore(score);
        interviewRepository.save(interview);

        return Map.of(
                "ok", true,
                "eventCount", mergedEvents.size(),
                "integrityScore", score
        );
    }

    @Transactional
    public Map<String, Object> saveSnapshot(String interviewId, MultipartFile file, String eventType) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("snapshot file is empty");
        }
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));

        Path dir = proctoringDir(interviewId);
        Files.createDirectories(dir);
        String safeType = eventType != null ? eventType.replaceAll("[^a-zA-Z0-9_\\-]", "") : "violation";
        String fileName = safeType + "_" + System.currentTimeMillis() + ".jpg";
        Path dest = dir.resolve(fileName);
        Files.write(dest, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        List<Map<String, Object>> snapshots = readJsonList(interview.getProctoringSnapshotsJson());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("at", Instant.now().toString());
        entry.put("eventType", safeType);
        entry.put("fileName", fileName);
        entry.put("path", dest.toAbsolutePath().toString());
        entry.put("bytes", Files.size(dest));
        snapshots.add(entry);
        if (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots = snapshots.subList(snapshots.size() - MAX_SNAPSHOTS, snapshots.size());
        }

        interview.setProctoringSnapshotsJson(objectMapper.writeValueAsString(snapshots));
        interviewRepository.save(interview);

        return Map.of(
                "ok", true,
                "fileName", fileName,
                "eventType", safeType,
                "snapshotCount", snapshots.size()
        );
    }

    public Map<String, Object> getTimeline(String interviewId) throws IOException {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));

        Map<String, Object> eventsDoc = readJsonMap(interview.getProctoringEventsJson());
        List<Map<String, Object>> snapshots = readJsonList(interview.getProctoringSnapshotsJson());

        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("interviewId", interviewId);
        timeline.put("integrityScore", interview.getProctoringScore());
        timeline.put("events", eventsDoc.getOrDefault("events", List.of()));
        timeline.put("strikes", eventsDoc.getOrDefault("strikes", Map.of()));
        timeline.put("status", eventsDoc.getOrDefault("status", "NOT_AVAILABLE"));
        timeline.put("summary", eventsDoc.getOrDefault("summary", Map.of()));
        timeline.put("updatedAt", eventsDoc.getOrDefault("updatedAt", null));
        timeline.put("snapshots", snapshots.stream().map(this::publicSnapshot).toList());
        return timeline;
    }

    public Path resolveSnapshot(String interviewId, String fileName) {
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid snapshot file name");
        }
        Path resolved = proctoringDir(interviewId).resolve(fileName).normalize();
        Path base = proctoringDir(interviewId).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Invalid snapshot path");
        }
        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException("Snapshot not found");
        }
        return resolved;
    }

    private Map<String, Object> publicSnapshot(Map<String, Object> snap) {
        Map<String, Object> pub = new LinkedHashMap<>(snap);
        pub.remove("path");
        return pub;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mergeEvents(Map<String, Object> existing, List<Map<String, Object>> incoming) {
        List<Map<String, Object>> merged = new ArrayList<>();
        Object existingEvents = existing.get("events");
        if (existingEvents instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    merged.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        if (incoming != null) {
            for (Map<String, Object> event : incoming) {
                if (event == null || event.isEmpty()) continue;
                String key = String.valueOf(event.get("at")) + "|" + String.valueOf(event.get("type"));
                boolean duplicate = merged.stream().anyMatch(e ->
                        key.equals(String.valueOf(e.get("at")) + "|" + String.valueOf(e.get("type"))));
                if (!duplicate) merged.add(new LinkedHashMap<>(event));
            }
        }
        if (merged.size() > MAX_EVENTS) {
            return merged.subList(merged.size() - MAX_EVENTS, merged.size());
        }
        return merged;
    }

    private int computeScoreFromEvents(List<Map<String, Object>> events) {
        int score = 100;
        Map<String, Integer> penalties = Map.of(
                "phone_detected", 25,
                "camera_blocked", 20,
                "multiple_faces", 30,
                "no_face", 10,
                "looking_away", 8
        );
        for (Map<String, Object> event : events) {
            String type = String.valueOf(event.getOrDefault("type", ""));
            score -= penalties.getOrDefault(type, 5);
        }
        return Math.max(0, Math.min(100, score));
    }

    private Map<String, Object> readJsonMap(String json) throws IOException {
        if (json == null || json.isBlank()) return new HashMap<>();
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private List<Map<String, Object>> readJsonList(String json) throws IOException {
        if (json == null || json.isBlank()) return new ArrayList<>();
        return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    }
}
