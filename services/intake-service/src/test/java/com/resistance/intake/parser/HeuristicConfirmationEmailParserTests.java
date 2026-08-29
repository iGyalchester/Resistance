package com.resistance.intake.parser;

import com.resistance.intake.service.InboundEmail;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicConfirmationEmailParserTests {

    private final HeuristicConfirmationEmailParser parser = new HeuristicConfirmationEmailParser();

    private InboundEmail email(String subject, String body) {
        return new InboundEmail("boris@gmail.com", "Boris Gerard", subject, body);
    }

    @Test
    void parsesCompanyAndPositionFromForwardedSubject() {
        Optional<ParsedApplication> result = parser.parse(email(
                "Fwd: Thank you for applying to Acme Corp",
                "We received your application for the Backend Engineer position at Acme Corp."));

        assertTrue(result.isPresent());
        assertEquals("Acme Corp", result.get().companyName());
        assertEquals("Backend Engineer", result.get().positionTitle());
    }

    @Test
    void parsesApplicationToCompanyForRolePhrase() {
        Optional<ParsedApplication> result = parser.parse(email(
                "Fwd: Application received",
                "Hi Boris,\n\nThanks! Your application to Globex for the Data Engineer role is in review.\n"));

        assertTrue(result.isPresent());
        assertEquals("Globex", result.get().companyName());
        assertEquals("Data Engineer", result.get().positionTitle());
    }

    @Test
    void companyOnlyStillParses() {
        Optional<ParsedApplication> result = parser.parse(email(
                "Fw: Fwd: We've received your application to Initech",
                "Our recruiting team will be in touch."));

        assertTrue(result.isPresent());
        assertEquals("Initech", result.get().companyName());
    }

    @Test
    void fallsBackToForwardedFromDisplayName() {
        Optional<ParsedApplication> result = parser.parse(email(
                "Fwd: Update on your submission",
                """
                ---------- Forwarded message ---------
                From: Umbrella Labs Careers <no-reply@greenhouse.io>
                Date: Mon, Aug 25, 2026
                Subject: Update on your submission

                Hello, thanks for your submission. We'll review it shortly.
                """));

        assertTrue(result.isPresent());
        assertEquals("Umbrella Labs", result.get().companyName());
    }

    @Test
    void fallsBackToForwardedFromDomain() {
        Optional<ParsedApplication> result = parser.parse(email(
                "Fwd: Submission received",
                """
                > From: no-reply@jobs.hooli.com
                > Subject: Submission received
                >
                > Hello, your submission is in our system.
                """));

        assertTrue(result.isPresent());
        assertEquals("Hooli", result.get().companyName());
    }

    @Test
    void unparseableEmailYieldsEmpty() {
        Optional<ParsedApplication> result = parser.parse(email(
                "Fwd: Lunch on Friday?",
                "Hey, are we still on for lunch?\n\nFrom: mom@gmail.com"));

        assertTrue(result.isEmpty());
    }
}
