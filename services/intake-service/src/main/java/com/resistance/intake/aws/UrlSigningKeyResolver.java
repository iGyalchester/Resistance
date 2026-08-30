package com.resistance.intake.aws;

import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Fetches and caches SNS signing certificates. The certificate URL comes
 * from the (attacker-controllable) message body, so it is validated to be
 * an HTTPS URL on an sns.<region>.amazonaws.com host before anything is
 * fetched - otherwise an attacker could point us at their own certificate.
 */
public class UrlSigningKeyResolver implements SigningKeyResolver {

    private static final Pattern SNS_HOST =
            Pattern.compile("^sns\\.[a-z0-9-]+\\.amazonaws\\.com(\\.cn)?$");

    private final RestClient restClient;
    private final Map<String, PublicKey> cache = new ConcurrentHashMap<>();

    public UrlSigningKeyResolver(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PublicKey resolve(String signingCertUrl) {
        validateCertUrl(signingCertUrl);
        return cache.computeIfAbsent(signingCertUrl, this::fetch);
    }

    static void validateCertUrl(String signingCertUrl) {
        if (signingCertUrl == null || signingCertUrl.isBlank()) {
            throw new IllegalArgumentException("SNS message has no SigningCertURL");
        }
        URI uri = URI.create(signingCertUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("SigningCertURL must be https: " + signingCertUrl);
        }
        String host = uri.getHost();
        if (host == null || !SNS_HOST.matcher(host.toLowerCase()).matches()) {
            throw new IllegalArgumentException("SigningCertURL host is not an SNS endpoint: " + host);
        }
    }

    private PublicKey fetch(String url) {
        try {
            String pem = restClient.get().uri(url).retrieve().body(String.class);
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            certificate.checkValidity();
            return certificate.getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load SNS signing certificate from " + url, e);
        }
    }
}
