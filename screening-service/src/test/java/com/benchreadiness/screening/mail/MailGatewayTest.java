package com.benchreadiness.screening.mail;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailGatewayTest {

    /** Counts deliveries and answers from a function, so each test decides the provider's behaviour. */
    private static final class FakeTransport implements MailTransport {
        final List<MailMessage> delivered = new ArrayList<>();
        Function<MailMessage, MailResult> behaviour = m -> MailResult.sent("id-" + (m.dedupKey() == null ? "x" : m.dedupKey()));

        @Override public String name() { return "fake"; }

        @Override public MailResult deliver(MailMessage message) {
            delivered.add(message);
            return behaviour.apply(message);
        }
    }

    private final FakeTransport transport = new FakeTransport();
    private ListAppender<ILoggingEvent> logs;
    private Logger gatewayLogger;

    @BeforeEach
    void captureLogs() {
        gatewayLogger = (Logger) LoggerFactory.getLogger(MailGateway.class);
        logs = new ListAppender<>();
        logs.start();
        gatewayLogger.addAppender(logs);
        gatewayLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachLogs() {
        gatewayLogger.detachAppender(logs);
    }

    private MailGateway gateway(long dedupSeconds, int maxPerSecond) {
        return new MailGateway(transport, dedupSeconds, maxPerSecond);
    }

    private static MailMessage mail(String event, String to) {
        return MailMessage.of(event, to, "Subject", "<p>SECRET-BODY-123456</p>");
    }

    private String allLogText() {
        StringBuilder text = new StringBuilder();
        logs.list.forEach(e -> text.append(e.getFormattedMessage()).append('\n'));
        return text.toString();
    }

    @Test
    void sameEventAndRecipientInsideTheWindowIsSentOnce() {
        MailGateway gateway = gateway(120, 0);

        assertTrue(gateway.send(mail("INVITE", "user@example.test")).isSent());
        assertTrue(gateway.send(mail("INVITE", "USER@example.test ")).isDuplicate());

        assertEquals(1, transport.delivered.size());
    }

    @Test
    void differentEventKeyOrRecipientIsNotADuplicate() {
        MailGateway gateway = gateway(120, 0);

        assertTrue(gateway.send(mail("INVITE", "a@example.test").withDedupKey("interview-1")).isSent());
        assertTrue(gateway.send(mail("INVITE", "a@example.test").withDedupKey("interview-2")).isSent());
        assertTrue(gateway.send(mail("INVITE", "b@example.test").withDedupKey("interview-1")).isSent());
        assertTrue(gateway.send(mail("CREDENTIALS", "a@example.test").withDedupKey("interview-1")).isSent());

        assertEquals(4, transport.delivered.size());
    }

    @Test
    void failedSendMayBeRetriedImmediately() {
        MailGateway gateway = gateway(120, 0);
        transport.behaviour = m -> MailResult.failed("http=500");

        assertTrue(gateway.send(mail("INVITE", "user@example.test")).isFailed());
        transport.behaviour = m -> MailResult.sent("id-2");
        assertTrue(gateway.send(mail("INVITE", "user@example.test")).isSent());

        assertEquals(2, transport.delivered.size());
    }

    @Test
    void zeroWindowDisablesTheDuplicateGuard() {
        MailGateway gateway = gateway(0, 0);

        assertTrue(gateway.send(mail("INVITE", "user@example.test")).isSent());
        assertTrue(gateway.send(mail("INVITE", "user@example.test")).isSent());
    }

    @Test
    void criticalMailSkipsTheDuplicateWindow() {
        MailGateway gateway = gateway(120, 0);

        assertTrue(gateway.send(mail("PASSWORD_RESET_OTP", "user@example.test").asCritical()).isSent());
        assertTrue(gateway.send(mail("PASSWORD_RESET_OTP", "user@example.test").asCritical()).isSent());

        assertEquals(2, transport.delivered.size());
    }

    @Test
    void bulkSendsAreSpacedButCriticalMailsAreNot() {
        MailGateway gateway = gateway(0, 20); // one mail per 50 ms

        long start = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            gateway.send(mail("CREDENTIALS", "user" + i + "@example.test"));
        }
        long bulkElapsed = System.currentTimeMillis() - start;
        assertTrue(bulkElapsed >= 140, "4 bulk mails at 20/s need about 150 ms, took " + bulkElapsed);

        long criticalStart = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            gateway.send(mail("PASSWORD_RESET_OTP", "otp" + i + "@example.test").asCritical());
        }
        assertTrue(System.currentTimeMillis() - criticalStart < 100, "critical mails must not wait for the throttle");
    }

    @Test
    void transportExceptionBecomesAFailureResult() {
        transport.behaviour = m -> { throw new IllegalStateException("boom"); };

        MailResult result = gateway(0, 0).send(mail("INVITE", "user@example.test"));

        assertTrue(result.isFailed());
        assertEquals("IllegalStateException", result.detail());
    }

    @Test
    void emptyRecipientFailsWithoutCallingTheProvider() {
        MailResult result = gateway(0, 0).send(mail("INVITE", "  "));

        assertTrue(result.isFailed());
        assertTrue(transport.delivered.isEmpty());
    }

    @Test
    void outcomeLineHasEventMaskedAddressProviderAndMessageIdOnly() {
        gateway(0, 0).send(mail("INVITE", "person@example.test").withDedupKey("k1"));

        String text = allLogText();
        assertTrue(text.contains("event=INVITE"));
        assertTrue(text.contains("outcome=SENT"));
        assertTrue(text.contains("provider=fake"));
        assertTrue(text.contains("to=p***@example.test"));
        assertTrue(text.contains("messageId=id-k1"));
        assertFalse(text.contains("person@example.test"), "full address must not be logged");
        assertFalse(text.contains("SECRET-BODY"), "body must not be logged");
    }

    @Test
    void failureAndDuplicateAreLoggedWithoutPersonalData() {
        MailGateway gateway = gateway(120, 0);
        transport.behaviour = m -> MailResult.failed("http=422 postmarkErrorCode=406");
        gateway.send(mail("INVITE", "person@example.test"));
        transport.behaviour = m -> MailResult.sent("id");
        gateway.send(mail("INVITE", "person@example.test"));
        gateway.send(mail("INVITE", "person@example.test"));

        String text = allLogText();
        assertTrue(text.contains("outcome=FAILED"));
        assertTrue(text.contains("postmarkErrorCode=406"));
        assertTrue(text.contains("outcome=DUPLICATE_SKIPPED"));
        assertFalse(text.contains("person@example.test"));
    }
}
