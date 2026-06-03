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

    @Value("${app.media.coqui-url:http://localhost:6014}")
    private String coquiUrl;

    public Map<String, Object> probe() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("whisperConfigured", whisperUrl != null && !whisperUrl.isBlank());
        out.put("coquiConfigured", coquiUrl != null && !coquiUrl.isBlank());
        boolean whisperOk = probeWhisper();
        boolean coquiOk = probeCoqui();
        out.put("whisperReachable", whisperOk);
        out.put("coquiReachable", coquiOk);
        out.put("mediaReady", whisperOk && coquiOk);
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

    private boolean probeCoqui() {
        if (coquiUrl == null || coquiUrl.isBlank()) return false;
        try {
            String base = coquiUrl.replaceAll("/$", "");
            URI uri = URI.create(base);
            String host = uri.getHost() != null ? uri.getHost() : "localhost";
            int port = uri.getPort() > 0 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 5000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
