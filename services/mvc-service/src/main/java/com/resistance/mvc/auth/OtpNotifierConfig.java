package com.resistance.mvc.auth;

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
public class OtpNotifierConfig {

    private static final Logger log = LoggerFactory.getLogger(OtpNotifierConfig.class);

    // real delivery when SMTP is configured (spring.mail.host set - works
    // with any SMTP server, including AWS SES's SMTP endpoint)
    @Bean
    @ConditionalOnProperty("spring.mail.host")
    public OtpNotifier emailOtpNotifier(JavaMailSender mailSender,
                                        @Value("${tracker.otp.from:no-reply@resistance.com}") String from) {
        return (account, code) -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(account.getEmail());
            message.setSubject("Your job tracker login code");
            message.setText("Hi " + account.getFullName() + ",\n\n"
                    + "Your one-time login code is: " + code + "\n\n"
                    + "It expires in 10 minutes. If you didn't request this, ignore this email.");
            mailSender.send(message);
        };
    }

    // dev fallback: log the code so the flow is testable with zero config
    @Bean
    @ConditionalOnMissingBean(OtpNotifier.class)
    public OtpNotifier loggingOtpNotifier() {
        return (account, code) ->
                log.info("DEV MODE - login code for {} is {}", account.getEmail(), code);
    }
}
