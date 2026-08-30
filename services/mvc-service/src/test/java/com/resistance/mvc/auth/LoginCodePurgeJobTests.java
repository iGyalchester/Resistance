package com.resistance.mvc.auth;

import com.resistance.mvc.dao.LoginCodeRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoginCodePurgeJobTests {

    @Test
    void purgesCodesExpiredLongerThanTheKeepWindow() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        LoginCodeRepository repository = mock(LoginCodeRepository.class);

        new LoginCodePurgeJob(repository, Clock.fixed(now, ZoneOffset.UTC)).purgeExpiredCodes();

        verify(repository).deleteByExpiresAtBefore(now.minus(LoginCodePurgeJob.KEEP_AFTER_EXPIRY));
    }
}
