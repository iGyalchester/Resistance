package com.resistance.mvc.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter for OTP requests, so an attacker can't bomb
 * someone's inbox with codes (or burn our email quota). In-memory and
 * per-instance - enough for this deployment shape; swap for a shared
 * store if the service ever scales out.
 */
public class OtpRequestThrottle {

    private record Window(Instant start, int count) {
    }

    private final int maxPerWindow;
    private final java.time.Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public OtpRequestThrottle(int maxPerWindow, java.time.Duration window, Clock clock) {
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Records an attempt for the key and says whether it is allowed.
     */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();

        Window result = windows.compute(key, (k, current) -> {
            if (current == null || now.isAfter(current.start().plus(window))) {
                return new Window(now, 1);
            }
            return new Window(current.start(), current.count() + 1);
        });

        // opportunistic cleanup so quiet keys don't accumulate forever
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now.isAfter(entry.getValue().start().plus(window)));
        }

        return result.count() <= maxPerWindow;
    }
}
