package com.benchreadiness.ops.mail;

/** One way of delivering a mail: the Postmark HTTP API or plain SMTP. Chosen by MAIL_PROVIDER. */
public interface MailTransport {

    String name();

    /** Delivers one mail. Reports failure in the result; must never put the body or the token in it. */
    MailResult deliver(MailMessage message);
}
