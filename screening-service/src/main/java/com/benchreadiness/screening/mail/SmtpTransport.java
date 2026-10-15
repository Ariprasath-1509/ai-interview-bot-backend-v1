package com.benchreadiness.screening.mail;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Sends through Spring's JavaMailSender (the MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD settings). */
public class SmtpTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final String from;
    private final String replyTo;

    public SmtpTransport(JavaMailSender mailSender, String from, String replyTo) {
        this.mailSender = mailSender;
        this.from = from;
        this.replyTo = replyTo;
    }

    @Override
    public String name() {
        return "smtp";
    }

    @Override
    public MailResult deliver(MailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.html(), true);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            mailSender.send(mime);
            return MailResult.sent(mime.getMessageID());
        } catch (Exception e) {
            return MailResult.failed(e.getClass().getSimpleName());
        }
    }
}
