package com.resistance.intake.audit;

import com.resistance.shared.utils.audit.AuditEventClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Emits intake's security-relevant moments (account provisioning,
 * email-driven application changes) to an AuditFlow ingestion endpoint.
 * Disabled unless tracker.audit.url is set; always fire-and-forget, so
 * auditing being down can never fail an intake transaction.
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
