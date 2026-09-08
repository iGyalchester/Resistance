package com.resistance.mvc.api;

import com.resistance.mvc.auth.SessionAuthenticator;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileApiControllerTests {

    private UserAccountRepository accounts;
    private AuditEventClient audit;
    private MockMvc mockMvc;
    private UserAccount boris;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        audit = mock(AuditEventClient.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileApiController(accounts, audit))
                .setControllerAdvice(new ApiErrorHandler())
                .build();
        boris = new UserAccount("Boris Gerard", "boris@gmail.com");
        boris.setId(7);
        boris.setPhone("+1 555 0100");
        when(accounts.findById(7)).thenReturn(Optional.of(boris));
    }

    @Test
    void readingTheProfileIsAuditedBecauseItHoldsThePhone() throws Exception {
        mockMvc.perform(get("/api/profile").sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Boris Gerard"))
                .andExpect(jsonPath("$.email").value("boris@gmail.com"))
                .andExpect(jsonPath("$.phone").value("+1 555 0100"));

        verify(audit).emit("FILE_ACCESS", "PROFILE_VIEW", "boris@gmail.com", "user_account:7", null);
    }

    @Test
    void updateChangesNameAndPhoneAndIsAudited() throws Exception {
        mockMvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" Boris G. \",\"phone\":\"  \"}")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Boris G."))
                .andExpect(jsonPath("$.phone").doesNotExist());

        assertEquals("Boris G.", boris.getFullName());
        assertNull(boris.getPhone());
        verify(accounts).save(boris);
        verify(audit).emit(eq("FILE_ACCESS"), eq("PROFILE_UPDATE"), eq("boris@gmail.com"),
                eq("user_account:7"), isNull());
    }

    @Test
    void blankNameIs400AndNothingIsSaved() throws Exception {
        mockMvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\"}")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.fullName").value("required"));

        verify(accounts, never()).save(any());
        verify(audit, never()).emit(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void anonymousCallsAre401AndUnaudited() throws Exception {
        mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\"}"))
                .andExpect(status().isUnauthorized());

        verify(audit, never()).emit(anyString(), anyString(), anyString(), anyString(), any());
    }
}
