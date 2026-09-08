package com.resistance.mvc.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaqServiceTests {

    @Test
    void faqFileParsesAndEveryEntryIsComplete() {
        FaqService faq = new FaqService();

        assertThat(faq.entries()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(faq.entries()).allSatisfy(entry -> {
            assertThat(entry.id()).isNotBlank();
            assertThat(entry.question()).isNotBlank();
            assertThat(entry.answer()).isNotBlank();
        });
        assertThat(faq.entries()).extracting(FaqEntry::id).doesNotHaveDuplicates();
        assertThat(faq.entries()).extracting(FaqEntry::id).contains("intake-address", "statuses", "assistant");
    }
}
