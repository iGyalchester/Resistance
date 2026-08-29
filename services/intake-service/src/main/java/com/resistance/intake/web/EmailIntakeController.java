package com.resistance.intake.web;

import com.resistance.intake.service.InboundEmail;
import com.resistance.intake.service.IntakeResult;
import com.resistance.intake.service.IntakeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plain JSON webhook: what a mail provider's inbound-parse hook (Mailgun,
 * SendGrid, Postmark) or a curl smoke test posts. Guarded by a shared
 * secret when intake.webhook-token is configured.
 */
@RestController
@RequestMapping("/intake")
public class EmailIntakeController {

    private final IntakeService intakeService;
    private final String webhookToken;

    public EmailIntakeController(IntakeService intakeService,
                                 @Value("${intake.webhook-token:}") String webhookToken) {
        this.intakeService = intakeService;
        this.webhookToken = webhookToken;
    }

    public record EmailPayload(String fromAddress, String fromName, String subject, String body) {
    }

    @PostMapping("/email")
    public ResponseEntity<IntakeResult> receive(
            @RequestHeader(value = "X-Intake-Token", required = false) String token,
            @RequestBody EmailPayload payload) {

        if (!webhookToken.isBlank() && !webhookToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        IntakeResult result = intakeService.process(new InboundEmail(
                payload.fromAddress(), payload.fromName(), payload.subject(), payload.body()));

        return ResponseEntity.ok(result);
    }
}
