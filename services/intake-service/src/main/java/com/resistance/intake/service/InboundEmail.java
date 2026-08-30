package com.resistance.intake.service;

/**
 * A received email, normalized from whichever inbound path delivered it
 * (plain webhook, AWS SES/SNS, or IMAP polling). toAddress is the intake
 * address the user forwarded to - when it carries a +alias tag, that alias
 * (not the spoofable From) decides which account the email belongs to.
 */
public record InboundEmail(String fromAddress, String fromName, String toAddress,
                           String subject, String body) {
}
