package com.benchreadiness.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Text-to-speech via Kokoro-FastAPI (OpenAI-compatible /v1/audio/speech).
 * Default Docker endpoint: http://kokoro-tts:8880, host port 6014.
 */
@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    @Value("${app.media.kokoro-url:http://localhost:6014}")
    private String kokoroUrl;

    @Value("${app.media.kokoro-voice:af_heart}")
    private String kokoroVoice;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isConfigured() {
        return kokoroUrl != null && !kokoroUrl.isBlank();
    }

    public byte[] synthesize(String text) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Kokoro TTS URL not configured");
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("text is required");
        }
        if (trimmed.length() > 2000) {
            trimmed = trimmed.substring(0, 2000);
        }

        String jsonBody = String.format(
            "{\"model\":\"kokoro\",\"input\":%s,\"voice\":\"%s\",\"speed\":1.0,\"response_format\":\"wav\"}",
            objectMapper.writeValueAsString(trimmed),
            kokoroVoice
        );

        String url = kokoroUrl.replaceAll("/$", "") + "/v1/audio/speech";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "audio/wav, audio/mpeg, audio/*")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String err = response.body() == null ? "" : new String(response.body(), 0,
                    Math.min(500, response.body().length), StandardCharsets.UTF_8);
            log.error("[TTS] Kokoro error {}: {}", response.statusCode(), err);
            throw new RuntimeException("Kokoro TTS error " + response.statusCode() + ": " + err);
        }

        byte[] audio = response.body();
        if (audio == null || audio.length == 0) {
            throw new RuntimeException("Kokoro TTS returned empty audio");
        }

        log.info("[TTS] Kokoro synthesized {} bytes for {} chars", audio.length, trimmed.length());
        return audio;
    }
}
