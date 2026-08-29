package com.resistance.intake.parser;

import com.resistance.intake.service.InboundEmail;

import java.util.Optional;

/**
 * Extracts the company and position from an application-confirmation email.
 * The default implementation is heuristic; an LLM-backed parser can be
 * dropped in behind this interface later.
 */
public interface ConfirmationEmailParser {

    Optional<ParsedApplication> parse(InboundEmail email);
}
