package com.resistance.intake.parser;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.resistance.intake.parser.claude.ClaudeConfirmationEmailParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Chooses the parser chain: heuristics alone when no Anthropic API key is
 * configured (dev default, fully offline), heuristics-then-Claude when
 * one is (set ANTHROPIC_API_KEY or intake.claude.api-key).
 */
@Configuration
public class ParserConfig {

    private static final Logger log = LoggerFactory.getLogger(ParserConfig.class);

    @Bean
    @Primary
    public ConfirmationEmailParser confirmationEmailParser(
            HeuristicConfirmationEmailParser heuristicParser,
            @Value("${intake.claude.api-key:${ANTHROPIC_API_KEY:}}") String apiKey,
            @Value("${intake.claude.model:claude-opus-5}") String model) {

        if (apiKey == null || apiKey.isBlank()) {
            log.info("No Anthropic API key configured - using heuristic email parsing only");
            return heuristicParser;
        }

        log.info("Claude email parsing enabled (model {}, heuristics first)", model);
        ClaudeConfirmationEmailParser claudeParser = new ClaudeConfirmationEmailParser(
                AnthropicOkHttpClient.builder().apiKey(apiKey).build(), model);
        return new FallbackConfirmationEmailParser(List.of(heuristicParser, claudeParser));
    }
}
