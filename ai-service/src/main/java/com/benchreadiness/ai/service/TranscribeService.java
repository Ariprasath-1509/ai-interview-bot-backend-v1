package com.benchreadiness.ai.service;

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
import java.util.Map;
import java.util.UUID;

/**
 * Server-side STT using Ollama's /api/transcribe endpoint.
 * Requires a whisper model pulled in Ollama, e.g.: ollama pull whisper
 * Falls back gracefully if not configured or model unavailable.
 */
@Service
public class TranscribeService {

    private static final Logger log = LoggerFactory.getLogger(TranscribeService.class);

    @Value("${app.ollama.base-url:http://127.0.0.1:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.transcribe-model:${APP_OLLAMA_TRANSCRIBE_MODEL:whisper}}")
    private String transcribeModel;

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isConfigured() {
        return ollamaBaseUrl != null && !ollamaBaseUrl.isBlank()
                && transcribeModel != null && !transcribeModel.isBlank();
    }

    /**
     * Transcribe audio via Ollama /api/transcribe.
     * Returns map with: text, language, provider
     */
    public Map<String, Object> transcribe(MultipartFile audio, String language) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Ollama transcribe not configured.");
        }

        String originalName = audio.getOriginalFilename();
        String ext = (originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".webm");
        File tmp = File.createTempFile("stt_" + UUID.randomUUID(), ext);
        try {
            audio.transferTo(tmp);
            return callOllamaTranscribe(tmp, language);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    private Map<String, Object> callOllamaTranscribe(File audioFile, String language) throws Exception {
        String boundary = "----BRSTTBoundary" + UUID.randomUUID().toString().replace("-", "");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        // model field
        writeField(body, boundary, "model", transcribeModel);
        // language field (optional)
        if (language != null && !language.isBlank() && !"auto".equals(language)) {
            writeField(body, boundary, "language", language);
        }
        // audio file field
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
            log.error("[STT] Ollama transcribe error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Ollama transcribe error " + response.statusCode() + ": " + response.body());
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(response.body());
        String text = json.path("text").asText("").trim();
        String detectedLang = json.path("language").asText(language != null ? language : "en");

        log.info("[STT] Transcribed {} chars, language={}", text.length(), detectedLang);
        return Map.of("text", text, "language", detectedLang, "provider", "ollama-whisper");
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
}
