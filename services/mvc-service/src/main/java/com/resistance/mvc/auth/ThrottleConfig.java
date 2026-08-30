package com.resistance.mvc.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class ThrottleConfig {

    // per email address: a person rarely needs more than a few codes
    @Bean
    public OtpRequestThrottle emailOtpThrottle(
            Clock clock,
            @Value("${tracker.otp.throttle.per-email:3}") int maxPerWindow,
            @Value("${tracker.otp.throttle.email-window-minutes:15}") long windowMinutes) {
        return new OtpRequestThrottle(maxPerWindow, Duration.ofMinutes(windowMinutes), clock);
    }

    // per source IP: caps bulk abuse across many addresses
    @Bean
    public OtpRequestThrottle ipOtpThrottle(
            Clock clock,
            @Value("${tracker.otp.throttle.per-ip:10}") int maxPerWindow,
            @Value("${tracker.otp.throttle.ip-window-minutes:60}") long windowMinutes) {
        return new OtpRequestThrottle(maxPerWindow, Duration.ofMinutes(windowMinutes), clock);
    }
}
