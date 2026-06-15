package com.benchreadiness.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Text-to-speech via Coqui service (default http://coqui-tts:5002 in Docker, host port 6014).
 */
@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    @Value("${app.media.coqui-url:http://localhost:6014}")
    private String coquiUrl;

    @Value("${app.media.tts-path:/api/tts}")
    private String ttsPath;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isConfigured() {
        return coquiUrl != null && !coquiUrl.isBlank();
    }

    public byte[] synthesize(String text) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Coqui TTS URL not configured");
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("text is required");
        }
        if (trimmed.length() > 2000) {
            trimmed = trimmed.substring(0, 2000);
        }

        // Coqui tts-server expects form-urlencoded text, not JSON (JSON body yields 500).
        String body = "text=" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
        String url = coquiUrl.replaceAll("/$", "") + ttsPath;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header("Accept", "audio/wav, audio/mpeg, audio/*, application/octet-stream, application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String err = response.body() == null ? "" : new String(response.body(), 0, Math.min(500, response.body().length), StandardCharsets.UTF_8);
            log.error("[TTS] Coqui error {}: {}", response.statusCode(), err);
            throw new RuntimeException("Coqui TTS error " + response.statusCode() + ": " + err);
        }

        byte[] audio = response.body();
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();

        if (contentType.contains("json")) {
            JsonNode json = objectMapper.readTree(audio);
            if (json.has("audio")) {
                String b64 = json.path("audio").asText("");
                if (!b64.isBlank()) {
                    audio = Base64.getDecoder().decode(b64);
                }
            } else if (json.has("audio_base64")) {
                audio = Base64.getDecoder().decode(json.path("audio_base64").asText(""));
            }
        }

        if (audio == null || audio.length == 0) {
            throw new RuntimeException("Coqui TTS returned empty audio");
        }

        log.info("[TTS] Synthesized {} bytes for {} chars", audio.length, trimmed.length());
        return audio;
    }
}
