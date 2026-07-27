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
 * Server-side STT.
 *
 * Priority:
 *   1. Sarvam STT (saarika:v2) — fast, Indian-language aware
 *   2. faster-whisper — fallback when Sarvam fails or key not set
 *   3. Ollama transcribe — legacy fallback when stt-provider=ollama
 *
 * Controlled by {@code app.media.stt-provider} (default: sarvam).
 * When sarvam is the provider but {@code app.media.sarvam-api-key} is blank,
 * the service automatically falls back to faster-whisper.
 */
@Service
public class TranscribeService {

    private static final Logger log = LoggerFactory.getLogger(TranscribeService.class);

    // ── STT provider selection ──────────────────────────────────────────────
    @Value("${app.media.stt-provider:sarvam}")
    private String sttProvider;

    // ── Sarvam config ───────────────────────────────────────────────────────
    @Value("${app.media.sarvam-api-key:}")
    private String sarvamApiKey;

    @Value("${app.media.sarvam-url:https://api.sarvam.ai}")
    private String sarvamUrl;

    @Value("${app.media.sarvam-model:saarika:v2}")
    private String sarvamModel;

    // ── Whisper config ──────────────────────────────────────────────────────
    @Value("${app.media.whisper-url:http://localhost:6013}")
    private String whisperUrl;

    @Value("${app.media.whisper-model:whisper-1}")
    private String whisperModel;

    // ── Ollama config (legacy) ──────────────────────────────────────────────
    @Value("${app.ollama.base-url:http://127.0.0.1:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.transcribe-model:${APP_OLLAMA_TRANSCRIBE_MODEL:whisper}}")
    private String ollamaTranscribeModel;

    private final ObjectMapper objectMapper;

    // Two clients: fast one for Sarvam (cloud API), slow one for Whisper/Ollama (local GPU).
    private final HttpClient fastHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public TranscribeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        if ("ollama".equalsIgnoreCase(sttProvider)) {
            return ollamaBaseUrl != null && !ollamaBaseUrl.isBlank();
        }
        if ("sarvam".equalsIgnoreCase(sttProvider)) {
            // Sarvam key OR a local Whisper URL must exist
            return (sarvamApiKey != null && !sarvamApiKey.isBlank())
                    || (whisperUrl != null && !whisperUrl.isBlank());
        }
        return whisperUrl != null && !whisperUrl.isBlank();
    }

    public Map<String, Object> transcribe(MultipartFile audio, String language) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("STT not configured. Set SARVAM_API_KEY or APP_MEDIA_WHISPER_URL.");
        }

        String ext = resolveExt(audio.getOriginalFilename());
        File tmp = File.createTempFile("stt_" + UUID.randomUUID(), ext);
        try {
            audio.transferTo(tmp);
            return doTranscribe(tmp, language);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    // ── Routing ─────────────────────────────────────────────────────────────

    private Map<String, Object> doTranscribe(File audio, String language) throws Exception {
        if ("ollama".equalsIgnoreCase(sttProvider)) {
            return callOllamaTranscribe(audio, language);
        }

        // sarvam (default) or faster-whisper — always try Sarvam first if key present
        if (sarvamApiKey != null && !sarvamApiKey.isBlank()) {
            try {
                return callSarvam(audio, language);
            } catch (Exception e) {
                log.warn("[STT] Sarvam failed ({}), falling back to faster-whisper", e.getMessage());
            }
        } else if ("sarvam".equalsIgnoreCase(sttProvider)) {
            log.warn("[STT] stt-provider=sarvam but SARVAM_API_KEY is not set — using faster-whisper");
        }

        return callFasterWhisper(audio, language);
    }

    // ── Sarvam STT ──────────────────────────────────────────────────────────

    private Map<String, Object> callSarvam(File audioFile, String language) throws Exception {
        String boundary = "----BRSTTBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        writeField(body, boundary, "model", sarvamModel);

        String sarvamLang = toSarvamLanguageCode(language);
        if (sarvamLang != null) {
            writeField(body, boundary, "language_code", sarvamLang);
        }

        byte[] audioBytes = Files.readAllBytes(audioFile.toPath());
        body.write(("--" + boundary + "\r\n").getBytes());
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio" + getExt(audioFile) + "\"\r\n").getBytes());
        body.write(("Content-Type: " + mimeType(audioFile) + "\r\n\r\n").getBytes());
        body.write(audioBytes);
        body.write("\r\n".getBytes());
        body.write(("--" + boundary + "--\r\n").getBytes());

        String url = sarvamUrl.replaceAll("/$", "") + "/speech-to-text";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("api-subscription-key", sarvamApiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> response = fastHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("[STT] Sarvam error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Sarvam STT error " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        String text = json.path("transcript").asText("").trim();
        String detectedLang = json.path("language_code").asText(sarvamLang != null ? sarvamLang : "en-IN");

        log.info("[STT] Sarvam transcribed {} chars (lang: {})", text.length(), detectedLang);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        result.put("language", detectedLang);
        result.put("provider", "sarvam");
        return result;
    }

    /** Map simple ISO-639-1 codes to Sarvam's BCP-47 language codes. */
    private String toSarvamLanguageCode(String lang) {
        if (lang == null || lang.isBlank() || "auto".equalsIgnoreCase(lang)) return null;
        Map<String, String> map = Map.ofEntries(
                Map.entry("en", "en-IN"),
                Map.entry("hi", "hi-IN"),
                Map.entry("ta", "ta-IN"),
                Map.entry("te", "te-IN"),
                Map.entry("kn", "kn-IN"),
                Map.entry("ml", "ml-IN"),
                Map.entry("mr", "mr-IN"),
                Map.entry("bn", "bn-IN"),
                Map.entry("gu", "gu-IN"),
                Map.entry("pa", "pa-IN"),
                Map.entry("or", "or-IN")
        );
        return map.getOrDefault(lang, lang.contains("-") ? lang : lang + "-IN");
    }

    // ── faster-whisper ──────────────────────────────────────────────────────

    private Map<String, Object> callFasterWhisper(File audioFile, String language) throws Exception {
        String boundary = "----BRSTTBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        if (shouldSendWhisperModel()) {
            writeField(body, boundary, "model", whisperModel);
        }
        if (language != null && !language.isBlank() && !"auto".equalsIgnoreCase(language)) {
            writeField(body, boundary, "language", language);
        }

        byte[] audioBytes = Files.readAllBytes(audioFile.toPath());
        body.write(("--" + boundary + "\r\n").getBytes());
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio" + getExt(audioFile) + "\"\r\n").getBytes());
        body.write(("Content-Type: " + mimeType(audioFile) + "\r\n\r\n").getBytes());
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

    // ── Ollama (legacy) ─────────────────────────────────────────────────────

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

    // ── Helpers ─────────────────────────────────────────────────────────────

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

    private String mimeType(File f) {
        String ext = getExt(f).toLowerCase();
        return switch (ext) {
            case ".mp3"  -> "audio/mpeg";
            case ".wav"  -> "audio/wav";
            case ".flac" -> "audio/flac";
            case ".ogg"  -> "audio/ogg";
            case ".mp4"  -> "audio/mp4";
            default      -> "audio/webm";
        };
    }

    private String resolveExt(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        return ".webm";
    }

    private boolean shouldSendWhisperModel() {
        if (whisperModel == null || whisperModel.isBlank()) return false;
        return !"whisper-1".equalsIgnoreCase(whisperModel);
    }
}
