package com.resistance.mvc.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpRequestThrottleTests {

    /** A clock the test can move forward. */
    private static class SteppingClock extends Clock {
        private final AtomicReference<Instant> now =
                new AtomicReference<>(Instant.parse("2026-08-30T12:00:00Z"));

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    void allowsUpToLimitThenBlocks() {
        OtpRequestThrottle throttle = new OtpRequestThrottle(3, Duration.ofMinutes(15), new SteppingClock());

        assertTrue(throttle.tryAcquire("email:a@b.com"));
        assertTrue(throttle.tryAcquire("email:a@b.com"));
        assertTrue(throttle.tryAcquire("email:a@b.com"));
        assertFalse(throttle.tryAcquire("email:a@b.com"));
        assertFalse(throttle.tryAcquire("email:a@b.com"));
    }

    @Test
    void windowExpiryResetsTheCount() {
        SteppingClock clock = new SteppingClock();
        OtpRequestThrottle throttle = new OtpRequestThrottle(2, Duration.ofMinutes(15), clock);

        assertTrue(throttle.tryAcquire("k"));
        assertTrue(throttle.tryAcquire("k"));
        assertFalse(throttle.tryAcquire("k"));

        clock.advance(Duration.ofMinutes(16));
        assertTrue(throttle.tryAcquire("k"));
    }

    @Test
    void keysAreIndependent() {
        OtpRequestThrottle throttle = new OtpRequestThrottle(1, Duration.ofMinutes(15), new SteppingClock());

        assertTrue(throttle.tryAcquire("email:a@b.com"));
        assertFalse(throttle.tryAcquire("email:a@b.com"));
        assertTrue(throttle.tryAcquire("email:c@d.com"));
        assertTrue(throttle.tryAcquire("ip:10.0.0.1"));
    }
}
