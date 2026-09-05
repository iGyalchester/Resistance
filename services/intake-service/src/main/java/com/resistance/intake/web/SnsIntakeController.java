package com.resistance.intake.web;

import com.resistance.intake.aws.RawMailStore;
import com.resistance.intake.aws.SnsSignatureVerifier;
import com.resistance.intake.mail.MimeText;
import com.resistance.intake.service.InboundEmail;
import com.resistance.intake.service.IntakeResult;
import com.resistance.intake.service.IntakeService;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Properties;

/**
 * AWS path: SES inbound receiving -> SNS topic -> this HTTPS subscription.
 * Handles the SNS handshake (SubscriptionConfirmation) and unwraps SES
 * Notification payloads into the same IntakeService flow as the plain
 * webhook. See infrastructure/aws/README.md for the SES/SNS chain and
 * infrastructure/terraform/modules/email-intake for the resources.
 */
@RestController
@RequestMapping("/intake")
public class SnsIntakeController {

    private static final Logger log = LoggerFactory.getLogger(SnsIntakeController.class);

    private final IntakeService intakeService;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;
    private final SnsSignatureVerifier signatureVerifier;
    private final RawMailStore rawMailStore;
    private final String expectedTopicArn;
    private final boolean verifySignature;

    public SnsIntakeController(IntakeService intakeService,
                               JsonMapper jsonMapper,
                               RestClient.Builder restClientBuilder,
                               SnsSignatureVerifier signatureVerifier,
                               RawMailStore rawMailStore,
                               @Value("${intake.aws.topic-arn:}") String expectedTopicArn,
                               @Value("${intake.aws.verify-signature:true}") boolean verifySignature) {
        this.intakeService = intakeService;
        this.jsonMapper = jsonMapper;
        this.restClient = restClientBuilder.build();
        this.signatureVerifier = signatureVerifier;
        this.rawMailStore = rawMailStore;
        this.expectedTopicArn = expectedTopicArn;
        this.verifySignature = verifySignature;
    }

    @PostMapping("/aws-sns")
    public ResponseEntity<?> receive(@RequestBody String rawBody) {
        JsonNode message = jsonMapper.readTree(rawBody);
        String type = text(message, "Type");
        String topicArn = text(message, "TopicArn");

        // the signature is the only field an attacker can't forge; the
        // TopicArn check below is defense-in-depth on top of it
        if (verifySignature && !signatureVerifier.isValid(message)) {
            log.warn("Rejecting SNS message with missing/invalid signature");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!expectedTopicArn.isBlank() && !expectedTopicArn.equals(topicArn)) {
            log.warn("Rejecting SNS message from unexpected topic {}", topicArn);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        switch (type == null ? "" : type) {
            case "SubscriptionConfirmation" -> {
                // completing the handshake requires fetching the SubscribeURL
                String subscribeUrl = text(message, "SubscribeURL");
                restClient.get().uri(subscribeUrl).retrieve().toBodilessEntity();
                log.info("Confirmed SNS subscription on topic {}", topicArn);
                return ResponseEntity.ok().build();
            }
            case "Notification" -> {
                IntakeResult result = intakeService.process(toInboundEmail(text(message, "Message")));
                return ResponseEntity.ok(result);
            }
            case "UnsubscribeConfirmation" -> {
                log.info("SNS unsubscribe confirmation received for topic {}", topicArn);
                return ResponseEntity.ok().build();
            }
            default -> {
                return ResponseEntity.badRequest().body("Unsupported SNS message type: " + type);
            }
        }
    }

    /**
     * The SNS Message field carries SES's JSON: mail metadata plus, when the
     * receipt rule is configured with it, the full base64 MIME content.
     */
    private InboundEmail toInboundEmail(String sesJson) {
        JsonNode ses = jsonMapper.readTree(sesJson);
        JsonNode mail = ses.path("mail");

        String fromAddress = mail.path("source").asString(null);
        String fromName = null;
        String toAddress = mail.path("destination").path(0).asString(null);
        String subject = mail.path("commonHeaders").path("subject").asString(null);
        String body = "";

        byte[] mime = mimeBytes(ses);
        if (mime != null) {
            try {
                Session session = Session.getInstance(new Properties());
                MimeMessage mimeMessage = new MimeMessage(session, new ByteArrayInputStream(mime));

                InternetAddress[] from = (InternetAddress[]) mimeMessage.getFrom();
                if (from != null && from.length > 0) {
                    fromAddress = from[0].getAddress();
                    fromName = from[0].getPersonal();
                }
                if (subject == null) {
                    subject = mimeMessage.getSubject();
                }
                body = MimeText.extract(mimeMessage);
            } catch (Exception e) {
                log.warn("Failed to parse SES MIME message, falling back to headers only", e);
            }
        }

        return new InboundEmail(fromAddress, fromName, toAddress, subject, body);
    }

    /**
     * The raw message, from the notification or from S3, or null.
     *
     * <p>Inline content first: an sns_action embeds base64 MIME directly,
     * which is what the local and test paths use. SNS refuses a notification
     * over 150 KB and SES bounces the mail, so in AWS the receipt rule uses
     * an s3_action instead - no ceiling below SES's own 40 MB - and the
     * notification names the object rather than carrying it.
     */
    private byte[] mimeBytes(JsonNode ses) {
        String messageId = ses.path("mail").path("messageId").asString("(unknown id)");

        String content = ses.path("content").asString(null);
        if (content != null && !content.isBlank()) {
            try {
                return Base64.getMimeDecoder().decode(content);
            } catch (IllegalArgumentException e) {
                log.warn("SES content for {} was not valid base64: {}", messageId, e.toString());
            }
        }

        JsonNode action = ses.path("receipt").path("action");
        if ("S3".equals(action.path("type").asString(null))) {
            String bucket = action.path("bucketName").asString(null);
            String key = action.path("objectKey").asString(null);
            byte[] fromS3 = rawMailStore.fetch(bucket, key).orElse(null);
            if (fromS3 == null) {
                log.warn("Archived message s3://{}/{} could not be read; filing {} from headers only",
                        bucket, key, messageId);
            }
            return fromS3;
        }

        log.warn("SES notification for {} carried neither inline content nor an S3 action; "
                + "filing from headers only", messageId);
        return null;
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asString(null);
    }
}
