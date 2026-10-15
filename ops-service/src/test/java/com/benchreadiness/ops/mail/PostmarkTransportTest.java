package com.benchreadiness.ops.mail;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Postmark transport against a local fake server. Only fake tokens and example.test addresses are used. */
class PostmarkTransportTest {

    private static final String FAKE_TOKEN = "fake-token-for-tests";

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestToken = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"ErrorCode\":0,\"Message\":\"OK\",\"MessageID\":\"msg-123\"}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/email", exchange -> {
            requestCount.incrementAndGet();
            requestToken.set(exchange.getRequestHeaders().getFirst("X-Postmark-Server-Token"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private PostmarkTransport transport(String token) {
        return new PostmarkTransport("http://127.0.0.1:" + server.getAddress().getPort(), token,
                "sender@example.test", "support@example.test", "outbound", 5);
    }

    private static MailMessage message() {
        return MailMessage.of("TEST_EVENT", "user@example.test", "Subject line", "<p>Body text</p>");
    }

    @Test
    void successReturnsTheProviderMessageId() {
        MailResult result = transport(FAKE_TOKEN).deliver(message());

        assertTrue(result.isSent());
        assertEquals("msg-123", result.messageId());
        assertEquals(FAKE_TOKEN, requestToken.get());
    }

    @Test
    void requestCarriesSenderRecipientStreamTagAndReplyTo() {
        transport(FAKE_TOKEN).deliver(message());

        String body = requestBody.get();
        assertTrue(body.contains("\"From\":\"sender@example.test\""));
        assertTrue(body.contains("\"To\":\"user@example.test\""));
        assertTrue(body.contains("\"Subject\":\"Subject line\""));
        assertTrue(body.contains("\"HtmlBody\":\"<p>Body text</p>\""));
        assertTrue(body.contains("\"MessageStream\":\"outbound\""));
        assertTrue(body.contains("\"Tag\":\"TEST_EVENT\""));
        assertTrue(body.contains("\"ReplyTo\":\"support@example.test\""));
    }

    @Test
    void providerRejectionIsAFailureWithCodesOnly() {
        responseStatus = 422;
        responseBody = "{\"ErrorCode\":406,\"Message\":\"Inactive recipient user@example.test\"}";

        MailResult result = transport(FAKE_TOKEN).deliver(message());

        assertTrue(result.isFailed());
        assertTrue(result.detail().contains("422"));
        assertTrue(result.detail().contains("406"));
        assertFalse(result.detail().contains("example.test"), "provider text can repeat the address");
    }

    @Test
    void okStatusWithAnErrorCodeIsStillAFailure() {
        responseBody = "{\"ErrorCode\":300,\"Message\":\"Invalid email request\"}";

        assertTrue(transport(FAKE_TOKEN).deliver(message()).isFailed());
    }

    @Test
    void missingTokenFailsWithoutCallingTheProvider() {
        MailResult result = transport("").deliver(message());

        assertTrue(result.isFailed());
        assertEquals(0, requestCount.get());
    }

    @Test
    void unreachableProviderIsAFailureNotAnException() {
        PostmarkTransport transport = transport(FAKE_TOKEN);
        server.stop(0);

        MailResult result = transport.deliver(message());

        assertTrue(result.isFailed());
        assertNotNull(result.detail());
        assertNull(result.messageId());
    }
}
