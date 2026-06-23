package com.benchreadiness.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MediaHealthService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${app.media.whisper-url:http://localhost:6013}")
    private String whisperUrl;

    @Value("${app.media.kokoro-url:http://localhost:6014}")
    private String kokoroUrl;

    public Map<String, Object> probe() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("whisperConfigured", whisperUrl != null && !whisperUrl.isBlank());
        out.put("kokoroConfigured", kokoroUrl != null && !kokoroUrl.isBlank());
        boolean whisperOk = probeWhisper();
        boolean kokoroOk = probeKokoro();
        out.put("whisperReachable", whisperOk);
        out.put("kokoroReachable", kokoroOk);
        out.put("mediaReady", whisperOk && kokoroOk);
        return out;
    }

    private boolean probeWhisper() {
        if (whisperUrl == null || whisperUrl.isBlank()) return false;
        try {
            String base = whisperUrl.replaceAll("/$", "");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/health"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() >= 200 && res.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean probeKokoro() {
        if (kokoroUrl == null || kokoroUrl.isBlank()) return false;
        try {
            String base = kokoroUrl.replaceAll("/$", "");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/health"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() >= 200 && res.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }
}
