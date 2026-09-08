package com.resistance.mvc.api;

import com.resistance.mvc.admin.AdminAccountView;
import com.resistance.mvc.admin.AdminOverview;
import com.resistance.mvc.admin.AdminService;
import com.resistance.shared.utils.audit.AuditEventClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The controller's own role check, with the security context set by hand
 * (the filter chain's rule is exercised in MvcServiceApplicationTests).
 */
class AdminApiControllerTests {

    private final AdminService admin = mock(AdminService.class);
    private final AuditEventClient audit = mock(AuditEventClient.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminApiController(admin, audit))
            .setControllerAdvice(new ApiErrorHandler())
            .build();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signedInAs(String email, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                email, null, List.of(roles).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void adminGetsTheOverviewAndTheReadIsAudited() throws Exception {
        signedInAs("ops@example.com", "ROLE_USER", "ROLE_ADMIN");
        when(admin.overview()).thenReturn(new AdminOverview(3, 12, Map.of("APPLIED", 12),
                List.of(new AdminOverview.DayCount(java.time.LocalDate.parse("2026-09-08"), 2)), List.of(), 0.25,
                new AdminOverview.AssistantStats(4, 1000, 200, 0, 1, 0),
                new AdminOverview.AuthStats(9, 1, 5, 2), Instant.parse("2026-09-08T06:00:00Z")));

        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").value(3))
                .andExpect(jsonPath("$.applications").value(12))
                .andExpect(jsonPath("$.unparsedTitleShare").value(0.25))
                .andExpect(jsonPath("$.intakeEventsLast30Days[0].day").value("2026-09-08"))
                .andExpect(jsonPath("$.assistant.inputTokens").value(1000))
                .andExpect(jsonPath("$.auth.loginFailure").value(2))
                .andExpect(jsonPath("$.countersSince").value("2026-09-08T06:00:00Z"));

        verify(audit).emit(eq("FILE_ACCESS"), eq("ADMIN_OVERVIEW"), eq("ops@example.com"), eq("admin"), isNull());
    }

    @Test
    void adminGetsTheAccountsWithoutPhonesOrAliases() throws Exception {
        signedInAs("ops@example.com", "ROLE_USER", "ROLE_ADMIN");
        when(admin.accounts()).thenReturn(List.of(new AdminAccountView(1, "boris@example.com", "Boris", 5,
                Instant.parse("2026-09-05T00:00:00Z"), true)));

        mockMvc.perform(get("/api/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("boris@example.com"))
                .andExpect(jsonPath("$[0].applicationCount").value(5))
                .andExpect(jsonPath("$[0].hasAlias").value(true))
                .andExpect(jsonPath("$[0].phone").doesNotExist())
                .andExpect(jsonPath("$[0].intakeAlias").doesNotExist());

        verify(audit).emit(eq("FILE_ACCESS"), eq("ADMIN_ACCOUNTS"), eq("ops@example.com"), eq("admin"), isNull());
    }

    @Test
    void plainUserIsForbiddenAndNothingIsReadOrAudited() throws Exception {
        signedInAs("boris@example.com", "ROLE_USER");

        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        mockMvc.perform(get("/api/admin/accounts")).andExpect(status().isForbidden());

        verify(admin, never()).overview();
        verify(admin, never()).accounts();
        verify(audit, never()).emit(any(), any(), any(), any(), any());
    }

    @Test
    void anonymousIsForbiddenToo() throws Exception {
        mockMvc.perform(get("/api/admin/overview")).andExpect(status().isForbidden());
        verify(admin, never()).overview();
    }
}
