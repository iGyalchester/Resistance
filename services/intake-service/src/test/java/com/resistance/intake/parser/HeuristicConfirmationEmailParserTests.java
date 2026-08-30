package com.resistance.intake.parser;

import com.resistance.intake.service.InboundEmail;
import com.resistance.shared.models.entity.ApplicationStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicConfirmationEmailParserTests {

    private final HeuristicConfirmationEmailParser parser = new HeuristicConfirmationEmailParser();

    private InboundEmail email(String subject, String body) {
        return new InboundEmail("boris@gmail.com", "Boris Gerard", null, subject, body);
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
    void confirmationDefaultsToAppliedWithNoContact() {
        ParsedApplication result = parser.parse(email(
                "Fwd: Thank you for applying to Acme Corp",
                """
                From: Acme Recruiting <no-reply@acme.com>

                We received your application for the Backend Engineer position at Acme Corp.
                """)).orElseThrow();

        assertEquals(ApplicationStatus.APPLIED, result.status());
        assertFalse(result.hasContact());
    }

    @Test
    void rejectionEmailDetectsRejectedStatus() {
        ParsedApplication result = parser.parse(email(
                "Fwd: Update on your application to Globex",
                "Thank you for your interest in Globex. Unfortunately, we have decided to "
                        + "move forward with other candidates for the Data Engineer role.")).orElseThrow();

        assertEquals("Globex", result.companyName());
        assertEquals(ApplicationStatus.REJECTED, result.status());
    }

    @Test
    void interviewInviteDetectsInterviewStatusAndRecruiterContact() {
        ParsedApplication result = parser.parse(email(
                "Fwd: Next steps for your application to Initech",
                """
                ---------- Forwarded message ---------
                From: Dana Reyes <dana.reyes@initech.com>
                Subject: Next steps

                Hi Boris, we'd like to schedule an interview for your application to Initech
                for the Java Developer role. When are you free next week?
                """)).orElseThrow();

        assertEquals("Initech", result.companyName());
        assertEquals(ApplicationStatus.INTERVIEW, result.status());
        assertTrue(result.hasContact());
        assertEquals("Dana Reyes", result.contactName());
        assertEquals("dana.reyes@initech.com", result.contactEmail());
    }

    @Test
    void offerEmailDetectsOfferStatus() {
        ParsedApplication result = parser.parse(email(
                "Fwd: Your offer letter",
                """
                From: Marcus Lee <marcus.lee@umbrellalabs.com>

                We are pleased to offer you the Platform Engineer position at Umbrella Labs!
                """)).orElseThrow();

        assertEquals(ApplicationStatus.OFFER, result.status());
        assertEquals("Marcus Lee", result.contactName());
    }

    @Test
    void robotSendersAreNotContacts() {
        ParsedApplication result = parser.parse(email(
                "Fwd: Interview scheduling",
                """
                From: Hooli Talent Team <notifications@hooli.com>

                We'd like to schedule an interview for your application to Hooli for the QA Engineer role.
                """)).orElseThrow();

        assertEquals(ApplicationStatus.INTERVIEW, result.status());
        assertFalse(result.hasContact());
    }

    @Test
    void unparseableEmailYieldsEmpty() {
        Optional<ParsedApplication> result = parser.parse(email(
                "Fwd: Lunch on Friday?",
                "Hey, are we still on for lunch?\n\nFrom: mom@gmail.com"));

        assertTrue(result.isEmpty());
    }
}
