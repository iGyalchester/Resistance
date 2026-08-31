package com.resistance.mvc.audit;

import com.resistance.shared.utils.audit.AuditEventClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Emits security-relevant moments (logins, data changes, PII access) to an
 * AuditFlow ingestion endpoint. Disabled unless tracker.audit.url is set,
 * and always fire-and-forget - auditing being down can never break login
 * or the app (an explicitly accepted at-most-once tradeoff).
 */
@Configuration
public class AuditConfig {

    @Bean
    public AuditEventClient auditEventClient(
            @Value("${tracker.audit.url:}") String url,
            @Value("${tracker.audit.token:}") String token,
            @Value("${tracker.audit.customer-id:resistance}") String customerId) {
        return new AuditEventClient(url, token, customerId);
    }
}
