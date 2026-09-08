package com.resistance.mvc.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Property binding and the on/off switch, without the rest of the app. */
class AssistantConfigTests {

    @Configuration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class, AssistantConfig.class)
            .withPropertyValues(
                    "tracker.ai.model=claude-opus-5", "tracker.ai.effort=medium", "tracker.ai.max-tokens=2048",
                    "tracker.ai.max-messages-per-hour=30", "tracker.ai.max-history-turns=20",
                    "tracker.ai.max-history-chars=30000");

    @Test
    void withoutAKeyTheFeatureIsOffAndTheModelRefusesToRun() {
        runner.withPropertyValues("tracker.ai.api-key=").run(context -> {
            AssistantProperties props = context.getBean(AssistantProperties.class);
            assertThat(props.enabled()).isFalse();
            assertThat(props.maxMessagesPerHour()).isEqualTo(30);
            AssistantModel model = context.getBean(AssistantModel.class);
            assertThatThrownBy(() -> model.run("", List.of(), List.of(), t -> { }))
                    .isInstanceOf(AssistantDisabledException.class);
        });
    }

    @Test
    void withAKeyTheClaudeAdapterIsWired() {
        runner.withPropertyValues("tracker.ai.api-key=sk-test").run(context -> {
            assertThat(context.getBean(AssistantProperties.class).enabled()).isTrue();
            assertThat(context.getBean(AssistantModel.class)).isInstanceOf(ClaudeAssistantModel.class);
            assertThat(context).hasBean("assistantThrottle");
        });
    }
}
