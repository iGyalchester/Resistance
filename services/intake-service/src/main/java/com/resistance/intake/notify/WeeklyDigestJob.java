package com.resistance.intake.notify;

import com.resistance.intake.dao.JobApplicationRepository;
import com.resistance.intake.dao.UserAccountRepository;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Monday-morning summary per account. Off by default; needs both
 * tracker.digest.enabled=true and SMTP configured.
 */
@Component
@ConditionalOnProperty(name = "tracker.digest.enabled", havingValue = "true")
public class WeeklyDigestJob {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestJob.class);

    private final UserAccountRepository accountRepository;
    private final JobApplicationRepository applicationRepository;
    private final JavaMailSender mailSender;
    private final String from;

    public WeeklyDigestJob(UserAccountRepository accountRepository,
                           JobApplicationRepository applicationRepository,
                           JavaMailSender mailSender,
                           @Value("${tracker.notifications.from:no-reply@resistance.com}") String from) {
        this.accountRepository = accountRepository;
        this.applicationRepository = applicationRepository;
        this.mailSender = mailSender;
        this.from = from;
    }

    @Scheduled(cron = "${tracker.digest.cron:0 0 13 * * MON}")
    public void sendDigests() {
        int sent = 0;
        for (UserAccount account : accountRepository.findAll()) {
            List<JobApplication> applications = applicationRepository.findByOwnerId(account.getId());
            if (applications.isEmpty()) {
                continue;
            }
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(account.getEmail());
                message.setSubject("Your weekly job application summary");
                message.setText(Notifications.digestBody(account, applications));
                mailSender.send(message);
                sent++;
            } catch (Exception e) {
                log.warn("Failed to send digest to {}", account.getEmail(), e);
            }
        }
        log.info("Weekly digest sent to {} account(s)", sent);
    }
}
