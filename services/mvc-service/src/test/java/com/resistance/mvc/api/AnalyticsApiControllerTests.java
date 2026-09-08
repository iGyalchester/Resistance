package com.resistance.mvc.api;

import com.resistance.mvc.analytics.AnalyticsService;
import com.resistance.mvc.analytics.AnalyticsView;
import com.resistance.mvc.auth.LoginController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsApiControllerTests {

    private final AnalyticsService analytics = mock(AnalyticsService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AnalyticsApiController(analytics))
            .setControllerAdvice(new ApiErrorHandler())
            .build();

    @Test
    void summaryIsComputedForTheSessionOwner() throws Exception {
        when(analytics.forOwner(7)).thenReturn(new AnalyticsView(5, 4,
                Map.of("APPLIED", 1), 0.8, 0.2, 15.5, Map.of("APPLIED", 15.5),
                List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/analytics/summary").sessionAttr(LoginController.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.responseRate").value(0.8))
                .andExpect(jsonPath("$.medianDaysInStage.APPLIED").value(15.5));
    }

    @Test
    void anonymousIs401AndComputesNothing() throws Exception {
        mockMvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isUnauthorized());

        verify(analytics, never()).forOwner(anyInt());
    }
}
