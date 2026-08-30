package com.resistance.intake.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class NotifierConfig {

    private static final Logger log = LoggerFactory.getLogger(NotifierConfig.class);

    // real delivery when SMTP is configured (SES SMTP works out of the box)
    @Bean
    @ConditionalOnProperty("spring.mail.host")
    public StatusNotifier emailStatusNotifier(JavaMailSender mailSender,
                                              @Value("${tracker.notifications.from:no-reply@resistance.com}") String from) {
        return (account, application, fromStatus) -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(account.getEmail());
            message.setSubject(Notifications.changeSubject(application, fromStatus));
            message.setText(Notifications.changeBody(account, application, fromStatus));
            mailSender.send(message);
        };
    }

    // dev fallback: visible in the log, no SMTP needed
    @Bean
    @ConditionalOnMissingBean(StatusNotifier.class)
    public StatusNotifier loggingStatusNotifier() {
        return (account, application, fromStatus) ->
                log.info("NOTIFY {} - {}", account.getEmail(),
                        Notifications.changeSubject(application, fromStatus));
    }
}
