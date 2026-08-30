package com.resistance.intake.aws;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnsSignatureVerifierTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final KeyPair keyPair = generateKeyPair();
    private final SnsSignatureVerifier verifier =
            new SnsSignatureVerifier(url -> keyPair.getPublic());

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ObjectNode notification() {
        ObjectNode message = jsonMapper.createObjectNode();
        message.put("Type", "Notification");
        message.put("MessageId", "mid-123");
        message.put("TopicArn", "arn:aws:sns:us-east-1:123456789012:resistance-intake");
        message.put("Subject", "Amazon SES Email Receipt Notification");
        message.put("Message", "{\"mail\":{}}");
        message.put("Timestamp", "2026-08-29T16:00:00.000Z");
        message.put("SigningCertURL", "https://sns.us-east-1.amazonaws.com/cert.pem");
        return message;
    }

    private void sign(ObjectNode message, String version, List<String> fields) throws Exception {
        String algorithm = "2".equals(version) ? "SHA256withRSA" : "SHA1withRSA";
        Signature signer = Signature.getInstance(algorithm);
        signer.initSign(keyPair.getPrivate());
        signer.update(SnsSignatureVerifier.canonicalString(message, fields).getBytes(StandardCharsets.UTF_8));
        message.put("SignatureVersion", version);
        message.put("Signature", Base64.getEncoder().encodeToString(signer.sign()));
    }

    private static final List<String> NOTIFICATION_FIELDS =
            List.of("Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type");

    @Test
    void validSignatureVersion1Passes() throws Exception {
        ObjectNode message = notification();
        sign(message, "1", NOTIFICATION_FIELDS);
        assertTrue(verifier.isValid(message));
    }

    @Test
    void validSignatureVersion2Passes() throws Exception {
        ObjectNode message = notification();
        sign(message, "2", NOTIFICATION_FIELDS);
        assertTrue(verifier.isValid(message));
    }

    @Test
    void tamperedMessageFails() throws Exception {
        ObjectNode message = notification();
        sign(message, "2", NOTIFICATION_FIELDS);
        message.put("Message", "{\"mail\":{\"source\":\"attacker@evil.example\"}}");
        assertFalse(verifier.isValid(message));
    }

    @Test
    void tamperedTopicArnFails() throws Exception {
        ObjectNode message = notification();
        sign(message, "2", NOTIFICATION_FIELDS);
        message.put("TopicArn", "arn:aws:sns:us-east-1:123456789012:expected-topic");
        assertFalse(verifier.isValid(message));
    }

    @Test
    void missingSignatureFails() {
        assertFalse(verifier.isValid(notification()));
    }

    @Test
    void subscriptionConfirmationCanonicalFieldsVerify() throws Exception {
        ObjectNode message = jsonMapper.createObjectNode();
        message.put("Type", "SubscriptionConfirmation");
        message.put("MessageId", "mid-456");
        message.put("Token", "tok");
        message.put("TopicArn", "arn:aws:sns:us-east-1:123456789012:resistance-intake");
        message.put("Message", "You have chosen to subscribe...");
        message.put("SubscribeURL", "https://sns.us-east-1.amazonaws.com/?Action=ConfirmSubscription");
        message.put("Timestamp", "2026-08-29T16:00:00.000Z");
        message.put("SigningCertURL", "https://sns.us-east-1.amazonaws.com/cert.pem");
        sign(message, "2",
                List.of("Message", "MessageId", "SubscribeURL", "Timestamp", "Token", "TopicArn", "Type"));
        assertTrue(verifier.isValid(message));
    }

    @Test
    void certUrlValidationRejectsNonSnsHosts() {
        assertThrows(IllegalArgumentException.class, () ->
                UrlSigningKeyResolver.validateCertUrl("https://evil.example/cert.pem"));
        assertThrows(IllegalArgumentException.class, () ->
                UrlSigningKeyResolver.validateCertUrl("http://sns.us-east-1.amazonaws.com/cert.pem"));
        assertThrows(IllegalArgumentException.class, () ->
                UrlSigningKeyResolver.validateCertUrl("https://sns.us-east-1.amazonaws.com.evil.example/cert.pem"));
        assertDoesNotThrow(() ->
                UrlSigningKeyResolver.validateCertUrl("https://sns.eu-west-1.amazonaws.com/SimpleNotificationService-abc.pem"));
    }
}
