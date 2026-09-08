package com.resistance.mvc.assistant;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.resistance.mvc.auth.OtpRequestThrottle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Wires the assistant. With no API key the model bean is a stub that
 * refuses to be called, the feature flag reads false, and nothing else
 * changes - the dev profile stays fully offline.
 */
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {

    private static final Logger log = LoggerFactory.getLogger(AssistantConfig.class);

    @Bean
    public AssistantModel assistantModel(AssistantProperties props) {
        if (!props.enabled()) {
            log.info("No Anthropic API key configured - the assistant is disabled");
            return (system, messages, tools, onText) -> {
                throw new AssistantDisabledException();
            };
        }
        log.info("Assistant enabled (model {}, effort {})", props.model(), props.effort());
        return new ClaudeAssistantModel(AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build(), props);
    }

    @Bean
    public AssistantTools assistantTools() {
        return new AssistantTools();
    }

    /** Per account: caps what one user can spend in an hour. */
    @Bean
    public OtpRequestThrottle assistantThrottle(Clock clock, AssistantProperties props) {
        return new OtpRequestThrottle(props.maxMessagesPerHour(), Duration.ofHours(1), clock);
    }

}
