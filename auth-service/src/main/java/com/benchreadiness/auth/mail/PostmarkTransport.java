package com.benchreadiness.auth.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sends through the Postmark HTTP API (POST /email). The token goes in a header and is never logged. */
public class PostmarkTransport implements MailTransport {

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final URI endpoint;
    private final String token;
    private final String from;
    private final String replyTo;
    private final String messageStream;
    private final Duration requestTimeout;

    public PostmarkTransport(String baseUrl, String token, String from, String replyTo,
                             String messageStream, int timeoutSeconds) {
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/email");
        this.token = token;
        this.from = from;
        this.replyTo = replyTo;
        this.messageStream = messageStream;
        this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.http = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    }

    @Override
    public String name() {
        return "postmark";
    }

    @Override
    public MailResult deliver(MailMessage message) {
        if (token == null || token.isBlank()) {
            return MailResult.failed("postmark token not configured");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("From", from);
            body.put("To", message.to());
            body.put("Subject", message.subject());
            body.put("HtmlBody", message.html());
            body.put("MessageStream", messageStream);
            body.put("Tag", message.event());
            if (replyTo != null && !replyTo.isBlank()) {
                body.put("ReplyTo", replyTo);
            }

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Postmark-Server-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            int errorCode = -1;
            String messageId = null;
            try {
                JsonNode node = json.readTree(response.body());
                errorCode = node.path("ErrorCode").asInt(-1);
                String id = node.path("MessageID").asText("");
                messageId = id.isEmpty() ? null : id;
            } catch (Exception ignored) {
                // body was not JSON: the HTTP status decides below
            }
            if (response.statusCode() == 200 && errorCode <= 0) {
                return MailResult.sent(messageId);
            }
            // Codes only: Postmark's own message text can repeat the recipient address
            return MailResult.failed("http=" + response.statusCode() + " postmarkErrorCode=" + errorCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return MailResult.failed("interrupted");
        } catch (Exception e) {
            return MailResult.failed(e.getClass().getSimpleName());
        }
    }
}
