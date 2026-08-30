package com.resistance.intake.aws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;

/**
 * Verifies SNS message signatures per the AWS spec: a canonical string is
 * built from the message fields (which fields depends on the message type),
 * then checked against the base64 Signature using the topic's signing
 * certificate. Without this, anything in the message body - including the
 * TopicArn we allowlist on - could be forged by anyone who knows the URL.
 */
public class SnsSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SnsSignatureVerifier.class);

    private static final List<String> NOTIFICATION_FIELDS =
            List.of("Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type");
    private static final List<String> SUBSCRIPTION_FIELDS =
            List.of("Message", "MessageId", "SubscribeURL", "Timestamp", "Token", "TopicArn", "Type");

    private final SigningKeyResolver keyResolver;

    public SnsSignatureVerifier(SigningKeyResolver keyResolver) {
        this.keyResolver = keyResolver;
    }

    public boolean isValid(JsonNode message) {
        try {
            String type = text(message, "Type");
            String signatureB64 = text(message, "Signature");
            String signatureVersion = text(message, "SignatureVersion");

            if (type == null || signatureB64 == null) {
                log.warn("SNS message missing Type or Signature");
                return false;
            }

            List<String> fields = switch (type) {
                case "Notification" -> NOTIFICATION_FIELDS;
                case "SubscriptionConfirmation", "UnsubscribeConfirmation" -> SUBSCRIPTION_FIELDS;
                default -> null;
            };
            if (fields == null) {
                log.warn("Unknown SNS message type '{}'", type);
                return false;
            }

            String algorithm = switch (signatureVersion == null ? "1" : signatureVersion) {
                case "1" -> "SHA1withRSA";
                case "2" -> "SHA256withRSA";
                default -> null;
            };
            if (algorithm == null) {
                log.warn("Unsupported SNS SignatureVersion '{}'", signatureVersion);
                return false;
            }

            PublicKey key = keyResolver.resolve(text(message, "SigningCertURL"));

            Signature signature = Signature.getInstance(algorithm);
            signature.initVerify(key);
            signature.update(canonicalString(message, fields).getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureB64));
        } catch (Exception e) {
            log.warn("SNS signature verification failed", e);
            return false;
        }
    }

    /**
     * "key\nvalue\n" for each present field, in the spec's field order.
     * Absent optional fields (e.g. Subject) are skipped entirely.
     */
    static String canonicalString(JsonNode message, List<String> fields) {
        StringBuilder canonical = new StringBuilder();
        for (String field : fields) {
            String value = text(message, field);
            if (value != null) {
                canonical.append(field).append('\n').append(value).append('\n');
            }
        }
        return canonical.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}
