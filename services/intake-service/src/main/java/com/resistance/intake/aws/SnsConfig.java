package com.resistance.intake.aws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Optional;

@Configuration
public class SnsConfig {

    private static final Logger log = LoggerFactory.getLogger(SnsConfig.class);

    @Bean
    public SnsSignatureVerifier snsSignatureVerifier(RestClient.Builder restClientBuilder) {
        return new SnsSignatureVerifier(new UrlSigningKeyResolver(restClientBuilder.build()));
    }

    /**
     * Reads the MIME SES archived, when a bucket is configured.
     *
     * <p>With no bucket the bean is a no-op that returns empty, so the
     * controller degrades to header-only parsing instead of the service
     * failing to start. Dev and the webhook/IMAP paths never touch S3, and a
     * missing optional integration should not stop the tracker booting.
     *
     * <p>The S3 client is built here rather than injected so that nothing is
     * constructed at all when the property is blank - the default credentials
     * chain would otherwise be probed on a machine that has no AWS identity.
     */
    @Bean
    public RawMailStore rawMailStore(@Value("${intake.aws.raw-mail-bucket:}") String bucket) {
        if (bucket == null || bucket.isBlank()) {
            log.info("No intake.aws.raw-mail-bucket configured; SES mail is parsed from the "
                    + "notification alone, so a message too large for SNS arrives without a body");
            return (b, k) -> Optional.empty();
        }
        log.info("Raw SES mail will be read from s3://{}", bucket);
        return new S3RawMailStore(S3Client.create());
    }
}
