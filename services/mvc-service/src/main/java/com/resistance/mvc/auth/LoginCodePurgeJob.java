package com.resistance.mvc.auth;

import com.resistance.mvc.dao.LoginCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Login codes are single-use and expire in minutes, but the rows would
 * otherwise accumulate forever. Sweep out codes that expired more than a
 * day ago (kept briefly for debugging) once an hour.
 */
@Component
public class LoginCodePurgeJob {

    static final Duration KEEP_AFTER_EXPIRY = Duration.ofDays(1);

    private static final Logger log = LoggerFactory.getLogger(LoginCodePurgeJob.class);

    private final LoginCodeRepository codeRepository;
    private final Clock clock;

    public LoginCodePurgeJob(LoginCodeRepository codeRepository, Clock clock) {
        this.codeRepository = codeRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tracker.otp.purge-ms:3600000}")
    @Transactional
    public void purgeExpiredCodes() {
        Instant cutoff = clock.instant().minus(KEEP_AFTER_EXPIRY);
        long removed = codeRepository.deleteByExpiresAtBefore(cutoff);
        if (removed > 0) {
            log.info("Purged {} expired login code(s)", removed);
        }
    }
}
