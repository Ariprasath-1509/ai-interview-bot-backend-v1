package com.benchreadiness.auth.mail;

/** Outcome of one send. {@code detail} never holds the mail body, a code, a password or a full address. */
public record MailResult(Status status, String messageId, String detail) {

    public enum Status { SENT, DUPLICATE, FAILED }

    public static MailResult sent(String messageId) {
        return new MailResult(Status.SENT, messageId, null);
    }

    public static MailResult duplicate() {
        return new MailResult(Status.DUPLICATE, null, "same event and recipient inside the duplicate window");
    }

    public static MailResult failed(String detail) {
        return new MailResult(Status.FAILED, null, detail);
    }

    public boolean isSent() {
        return status == Status.SENT;
    }

    public boolean isDuplicate() {
        return status == Status.DUPLICATE;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }
}
