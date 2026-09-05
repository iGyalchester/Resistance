package com.resistance.intake.web;

import com.resistance.intake.aws.SnsSignatureVerifier;
import com.resistance.intake.service.InboundEmail;
import com.resistance.intake.service.IntakeResult;
import com.resistance.intake.service.IntakeService;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The SES -> SNS path, exercised the way SES actually calls it. The first
 * two tests are the reason the receipt rule needs an sns_action: SES puts
 * the base64 MIME message in the notification's "content" field only for
 * that action, and everything the parser works with comes out of it.
 * Signature verification is off here - it has its own test.
 */
class SnsIntakeControllerTests {

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:resistance-dev-intake";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private IntakeService intakeService;
    private SnsIntakeController controller;

    @BeforeEach
    void setUp() {
        intakeService = mock(IntakeService.class);
        when(intakeService.process(any())).thenReturn(IntakeResult.ignored("IGNORED_NO_ALIAS"));
        controller = new SnsIntakeController(intakeService, jsonMapper, RestClient.builder(),
                new SnsSignatureVerifier(url -> null), TOPIC_ARN, false);
    }

    @Test
    void theMimeContentBecomesTheEmailBody() throws Exception {
        String content = base64Mime("careers@acme-recruiting.example", "Acme Recruiting",
                "Thank you for applying to Acme",
                "Hi Boris,\n\nWe received your application for Backend Engineer.\n");

        ResponseEntity<?> response = controller.receive(sns("Notification", TOPIC_ARN, ses(content)));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        InboundEmail email = processedEmail();
        assertTrue(email.body().contains("We received your application for Backend Engineer"),
                "the MIME body should reach the parser, was: " + email.body());
        // the MIME From wins over the envelope sender, which for a forwarded
        // confirmation is a bounce address nobody wants to see
        assertEquals("careers@acme-recruiting.example", email.fromAddress());
        assertEquals("Acme Recruiting", email.fromName());
        assertEquals("track+boris@dev.resistance.example", email.toAddress());
        assertEquals("Envelope subject", email.subject());
    }

    @Test
    void withoutMimeContentOnlyTheHeadersSurvive() {
        ResponseEntity<?> response = controller.receive(sns("Notification", TOPIC_ARN, ses(null)));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        InboundEmail email = processedEmail();
        // what an s3_action-only receipt rule produced: nothing to parse
        assertEquals("", email.body());
        assertEquals("bounce@acme-recruiting.example", email.fromAddress());
        assertNull(email.fromName());
    }

    @Test
    void unreadableMimeContentFallsBackToTheHeaders() {
        String notMime = Base64.getEncoder().encodeToString(new byte[]{0x00, 0x01, 0x02});

        ResponseEntity<?> response = controller.receive(sns("Notification", TOPIC_ARN, ses(notMime)));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bounce@acme-recruiting.example", processedEmail().fromAddress());
    }

    @Test
    void aNotificationFromAnotherTopicIsRejected() {
        String other = "arn:aws:sns:us-east-1:123456789012:someone-elses-topic";

        ResponseEntity<?> response = controller.receive(sns("Notification", other, ses(null)));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(intakeService, never()).process(any());
    }

    @Test
    void anUnknownMessageTypeIsRejected() {
        ResponseEntity<?> response = controller.receive(sns("Nonsense", TOPIC_ARN, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(intakeService, never()).process(any());
    }

    private InboundEmail processedEmail() {
        ArgumentCaptor<InboundEmail> captor = ArgumentCaptor.forClass(InboundEmail.class);
        verify(intakeService).process(captor.capture());
        return captor.getValue();
    }

    /** The SNS envelope intake-service receives over HTTPS. */
    private String sns(String type, String topicArn, String sesJson) {
        ObjectNode message = jsonMapper.createObjectNode();
        message.put("Type", type);
        message.put("MessageId", "mid-1");
        message.put("TopicArn", topicArn);
        message.put("Timestamp", "2026-09-04T16:00:00.000Z");
        if (sesJson != null) {
            message.put("Message", sesJson);
        }
        return jsonMapper.writeValueAsString(message);
    }

    /** SES's own JSON, carried as a string in the envelope's Message field. */
    private String ses(String base64Content) {
        ObjectNode ses = jsonMapper.createObjectNode();
        ses.put("notificationType", "Received");
        ObjectNode mail = ses.putObject("mail");
        mail.put("source", "bounce@acme-recruiting.example");
        mail.putArray("destination").add("track+boris@dev.resistance.example");
        mail.putObject("commonHeaders").put("subject", "Envelope subject");
        if (base64Content != null) {
            ses.put("content", base64Content);
        }
        return jsonMapper.writeValueAsString(ses);
    }

    private String base64Mime(String address, String personal, String subject, String body) throws Exception {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        mime.setFrom(new InternetAddress(address, personal));
        mime.setSubject(subject);
        mime.setText(body);
        mime.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mime.writeTo(out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
