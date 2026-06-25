package com.benchreadiness.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side STT: primary faster-whisper-server (host 6013 → container 8000),
 * optional fallback to Ollama /api/transcribe.
 */
@Service
public class TranscribeService {

    private static final Logger log = LoggerFactory.getLogger(TranscribeService.class);

    @Value("${app.media.stt-provider:faster-whisper}")
    private String sttProvider;

    @Value("${app.media.whisper-url:http://localhost:6013}")
    private String whisperUrl;

    @Value("${app.media.whisper-model:whisper-1}")
    private String whisperModel;

    @Value("${app.ollama.base-url:http://127.0.0.1:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.transcribe-model:${APP_OLLAMA_TRANSCRIBE_MODEL:whisper}}")
    private String ollamaTranscribeModel;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public TranscribeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        if ("ollama".equalsIgnoreCase(sttProvider)) {
            return ollamaBaseUrl != null && !ollamaBaseUrl.isBlank()
                    && ollamaTranscribeModel != null && !ollamaTranscribeModel.isBlank();
        }
        return whisperUrl != null && !whisperUrl.isBlank();
    }

    public Map<String, Object> transcribe(MultipartFile audio, String language) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("STT not configured (set app.media.whisper-url or Ollama).");
        }

        String originalName = audio.getOriginalFilename();
        String ext = (originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".webm");
        File tmp = File.createTempFile("stt_" + UUID.randomUUID(), ext);
        try {
            audio.transferTo(tmp);
            if (!"ollama".equalsIgnoreCase(sttProvider)) {
                try {
                    return callFasterWhisper(tmp, language);
                } catch (Exception e) {
                    log.warn("[STT] faster-whisper failed: {}", e.getMessage());
                    throw e;
                }
            }
            return callOllamaTranscribe(tmp, language);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    private Map<String, Object> callFasterWhisper(File audioFile, String language) throws Exception {
        String boundary = "----BRSTTBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        // Let faster-whisper-server use WHISPER_MODEL from its container env unless a full HF id is configured.
        if (shouldSendWhisperModel()) {
            writeField(body, boundary, "model", whisperModel);
        }
        if (language != null && !language.isBlank() && !"auto".equalsIgnoreCase(language)) {
            writeField(body, boundary, "language", language);
        }
        byte[] audioBytes = Files.readAllBytes(audioFile.toPath());
        body.write(("--" + boundary + "\r\n").getBytes());
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio" + getExt(audioFile) + "\"\r\n").getBytes());
        String contentType = getExt(audioFile).equalsIgnoreCase(".webm")
                ? "audio/webm"
                : "application/octet-stream";
        body.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes());
        body.write(audioBytes);
        body.write("\r\n".getBytes());
        body.write(("--" + boundary + "--\r\n").getBytes());

        String url = whisperUrl.replaceAll("/$", "") + "/v1/audio/transcriptions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("[STT] faster-whisper error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("faster-whisper error " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        String text = json.path("text").asText("").trim();
        String detectedLang = json.path("language").asText(language != null ? language : "en");

        log.info("[STT] faster-whisper transcribed {} chars", text.length());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        result.put("language", detectedLang);
        result.put("provider", "faster-whisper");
        return result;
    }

    private Map<String, Object> callOllamaTranscribe(File audioFile, String language) throws Exception {
        String boundary = "----BRSTTBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeField(body, boundary, "model", ollamaTranscribeModel);
        if (language != null && !language.isBlank() && !"auto".equalsIgnoreCase(language)) {
            writeField(body, boundary, "language", language);
        }
        byte[] audioBytes = Files.readAllBytes(audioFile.toPath());
        body.write(("--" + boundary + "\r\n").getBytes());
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio" + getExt(audioFile) + "\"\r\n").getBytes());
        body.write(("Content-Type: audio/webm\r\n\r\n").getBytes());
        body.write(audioBytes);
        body.write("\r\n".getBytes());
        body.write(("--" + boundary + "--\r\n").getBytes());

        String url = ollamaBaseUrl.replaceAll("/$", "") + "/api/transcribe";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama transcribe error " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        String text = json.path("text").asText("").trim();
        String detectedLang = json.path("language").asText(language != null ? language : "en");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        result.put("language", detectedLang);
        result.put("provider", "ollama-whisper");
        return result;
    }

    private void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes());
        out.write((value + "\r\n").getBytes());
    }

    private String getExt(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i >= 0 ? n.substring(i) : ".webm";
    }

    private boolean shouldSendWhisperModel() {
        if (whisperModel == null || whisperModel.isBlank()) return false;
        return !"whisper-1".equalsIgnoreCase(whisperModel);
    }
}
