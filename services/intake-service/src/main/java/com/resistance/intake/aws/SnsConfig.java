package com.resistance.intake.aws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SnsConfig {

    @Bean
    public SnsSignatureVerifier snsSignatureVerifier(RestClient.Builder restClientBuilder) {
        return new SnsSignatureVerifier(new UrlSigningKeyResolver(restClientBuilder.build()));
    }
}
