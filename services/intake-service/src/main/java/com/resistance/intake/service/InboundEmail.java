package com.resistance.intake.service;

/**
 * A received email, normalized from whichever inbound path delivered it
 * (plain webhook, AWS SES/SNS, or IMAP polling).
 */
public record InboundEmail(String fromAddress, String fromName, String subject, String body) {
}
