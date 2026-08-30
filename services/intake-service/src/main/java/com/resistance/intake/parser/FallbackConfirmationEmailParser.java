package com.resistance.intake.parser;

import com.resistance.intake.service.InboundEmail;

import java.util.List;
import java.util.Optional;

/**
 * Tries parsers in order and returns the first result. Used to run the
 * free, deterministic heuristics first and only spend a Claude call on
 * the emails they can't handle.
 */
public class FallbackConfirmationEmailParser implements ConfirmationEmailParser {

    private final List<ConfirmationEmailParser> delegates;

    public FallbackConfirmationEmailParser(List<ConfirmationEmailParser> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public Optional<ParsedApplication> parse(InboundEmail email) {
        for (ConfirmationEmailParser delegate : delegates) {
            Optional<ParsedApplication> result = delegate.parse(email);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
