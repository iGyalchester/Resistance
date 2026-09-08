package com.resistance.mvc.assistant;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The FAQ has one source of truth, a JSON file on the classpath, read
 * once at startup. The Help page serves it and the assistant's prompt
 * embeds it, so the two can never disagree.
 */
@Service
public class FaqService {

    static final String RESOURCE = "assistant/faq.json";

    private final List<FaqEntry> entries;

    public FaqService() {
        this(RESOURCE);
    }

    FaqService(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            List<FaqEntry> loaded = JsonMapper.builder().build()
                    .readValue(in, JsonMapper.builder().build().getTypeFactory()
                            .constructCollectionType(List.class, FaqEntry.class));
            loaded.forEach(FaqService::validate);
            this.entries = List.copyOf(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + resource, e);
        }
    }

    private static void validate(FaqEntry entry) {
        if (blank(entry.id()) || blank(entry.question()) || blank(entry.answer())) {
            throw new IllegalStateException("FAQ entry is missing a field: " + entry);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public List<FaqEntry> entries() {
        return entries;
    }
}
