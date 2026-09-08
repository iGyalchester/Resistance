package com.resistance.mvc.api;

import com.resistance.mvc.assistant.FaqService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HelpApiControllerTests {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new HelpApiController(new FaqService()))
            .build();

    @Test
    void servesTheFaqWithoutASession() throws Exception {
        mockMvc.perform(get("/api/help"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(9)))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].question").isNotEmpty())
                .andExpect(jsonPath("$[0].answer").isNotEmpty());
    }
}
