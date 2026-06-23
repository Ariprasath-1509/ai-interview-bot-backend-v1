package com.benchreadiness.interview.service;

import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.repository.InterviewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

@Service
public class RecordingService {

    private final InterviewRepository interviewRepository;

    @Value("${app.recording.upload-dir:${java.io.tmpdir}/br-recordings}")
    private String uploadDir;

    public RecordingService(InterviewRepository interviewRepository) {
        this.interviewRepository = interviewRepository;
    }

    private Path recordingPath(String interviewId) {
        return Paths.get(uploadDir).resolve("rec_" + interviewId + ".webm");
    }

    @Transactional
    public Map<String, Object> appendChunk(String interviewId, MultipartFile chunk, int chunkIndex, boolean isFinal)
            throws IOException {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Path dest = recordingPath(interviewId);

        if (chunk != null && !chunk.isEmpty()) {
            byte[] bytes = chunk.getBytes();
            if (chunkIndex <= 0) {
                Files.write(dest, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Files.write(dest, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } else if (chunkIndex <= 0) {
            throw new IllegalArgumentException("chunk is empty");
        }

        long bytes = Files.exists(dest) ? Files.size(dest) : 0L;

        if (isFinal) {
            if (!Files.exists(dest)) {
                throw new IllegalArgumentException("no recording data to finalize");
            }
            interview.setRecordingPath(dest.toAbsolutePath().toString());
            interview.setRecordingBytes(bytes);
            interviewRepository.save(interview);
        }

        return Map.of(
            "ok", true,
            "chunkIndex", chunkIndex,
            "finalized", isFinal,
            "path", dest.getFileName().toString(),
            "bytes", bytes
        );
    }

    /** Legacy single-shot upload (kept for backward compatibility). */
    @Transactional
    public Map<String, Object> uploadFull(String interviewId, MultipartFile file) throws IOException {
        return appendChunk(interviewId, file, 0, true);
    }
}
