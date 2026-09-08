package com.resistance.mvc.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Login-flow counters for the admin view: codes requested, requests the
 * throttle refused, logins that succeeded and failed. Micrometer counters
 * live in this process's memory, so they start at zero on every restart
 * and, with several tasks, each task counts its own share; the admin
 * page says so. Good enough to spot a brute-force attempt or a broken
 * email path at a glance, which is what they are for.
 */
@Component
public class AuthMetrics {

    public static final String OTP_REQUESTED = "auth.otp.requested";
    public static final String OTP_THROTTLED = "auth.otp.throttled";
    public static final String LOGIN_SUCCESS = "auth.login.success";
    public static final String LOGIN_FAILURE = "auth.login.failure";

    private final Counter otpRequested;
    private final Counter otpThrottled;
    private final Counter loginSuccess;
    private final Counter loginFailure;

    public AuthMetrics(MeterRegistry meters) {
        this.otpRequested = meters.counter(OTP_REQUESTED);
        this.otpThrottled = meters.counter(OTP_THROTTLED);
        this.loginSuccess = meters.counter(LOGIN_SUCCESS);
        this.loginFailure = meters.counter(LOGIN_FAILURE);
    }

    public void otpRequested() {
        otpRequested.increment();
    }

    public void otpThrottled() {
        otpThrottled.increment();
    }

    public void loginSuccess() {
        loginSuccess.increment();
    }

    public void loginFailure() {
        loginFailure.increment();
    }
}
