package com.benchreadiness.ops.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Picks the mail transport from MAIL_PROVIDER: "postmark" (HTTP API) or "smtp" (the default, as before). */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    public MailTransport mailTransport(
            JavaMailSender javaMailSender,
            @Value("${app.mail.provider:smtp}") String provider,
            @Value("${app.mail.from:noreply@voiceaibot.in}") String from,
            @Value("${app.mail.reply-to:}") String replyTo,
            @Value("${app.mail.postmark.base-url:https://api.postmarkapp.com}") String postmarkBaseUrl,
            @Value("${app.mail.postmark.token:}") String postmarkToken,
            @Value("${app.mail.postmark.stream:outbound}") String postmarkStream,
            @Value("${app.mail.timeout-seconds:10}") int timeoutSeconds) {
        if ("postmark".equalsIgnoreCase(provider.trim())) {
            if (postmarkToken == null || postmarkToken.isBlank()) {
                log.warn("MAIL_PROVIDER=postmark but POSTMARK_SERVER_TOKEN is empty: every mail will fail until it is set");
            }
            log.info("Mail provider: postmark (stream={})", postmarkStream);
            return new PostmarkTransport(postmarkBaseUrl, postmarkToken, from, replyTo, postmarkStream, timeoutSeconds);
        }
        log.info("Mail provider: smtp");
        return new SmtpTransport(javaMailSender, from, replyTo);
    }
}
