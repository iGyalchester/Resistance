package com.resistance.mvc.api;

import com.resistance.mvc.assistant.FaqEntry;
import com.resistance.mvc.assistant.FaqService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The Help page's questions and answers. Public: nothing here is per-user. */
@RestController
public class HelpApiController {

    private final FaqService faq;

    public HelpApiController(FaqService faq) {
        this.faq = faq;
    }

    @GetMapping("/api/help")
    public List<FaqEntry> help() {
        return faq.entries();
    }
}
