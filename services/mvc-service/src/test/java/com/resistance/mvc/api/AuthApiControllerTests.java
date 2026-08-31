package com.resistance.mvc.api;

import com.resistance.mvc.auth.LoginController;
import com.resistance.mvc.auth.OtpRequestThrottle;
import com.resistance.mvc.auth.OtpService;
import com.resistance.mvc.auth.SessionAuthenticator;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiControllerTests {

    private OtpService otpService;
    private OtpRequestThrottle emailThrottle;
    private OtpRequestThrottle ipThrottle;
    private UserAccountRepository accounts;
    private MockMvc mockMvc;
    private UserAccount boris;

    @BeforeEach
    void setUp() {
        otpService = mock(OtpService.class);
        emailThrottle = mock(OtpRequestThrottle.class);
        ipThrottle = mock(OtpRequestThrottle.class);
        accounts = mock(UserAccountRepository.class);

        // real authenticator + repository: the login test should prove the
        // session actually ends up authenticated, not that a mock was called
        SessionAuthenticator authenticator =
                new SessionAuthenticator(new HttpSessionSecurityContextRepository());

        AuthApiController controller = new AuthApiController(otpService, authenticator,
                emailThrottle, ipThrottle, accounts, "track@resistance.example",
                com.resistance.shared.utils.audit.AuditEventClient.disabled());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        boris = new UserAccount("Boris Gerard", "boris@gmail.com");
        boris.setId(7);
        boris.setIntakeAlias("boris2k4mp9");
    }

    @Test
    void codeRequestAnswersGenericallyAndIssuesCode() throws Exception {
        when(emailThrottle.tryAcquire(anyString())).thenReturn(true);
        when(ipThrottle.tryAcquire(anyString())).thenReturn(true);

        mockMvc.perform(post("/api/auth/code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"boris@gmail.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(otpService).requestCode("boris@gmail.com");
    }

    @Test
    void throttledCodeRequestAnswersIdenticallyButIssuesNothing() throws Exception {
        when(emailThrottle.tryAcquire(anyString())).thenReturn(false);

        String allowedBody = "{\"message\":\"If that address is known, a code is on its way.\"}";
        mockMvc.perform(post("/api/auth/code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"boris@gmail.com\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(allowedBody, result.getResponse().getContentAsString()));

        verify(otpService, never()).requestCode(anyString());
    }

    @Test
    void loginWithValidCodeAuthenticatesTheSession() throws Exception {
        when(otpService.verify("boris@gmail.com", "123456")).thenReturn(Optional.of(boris));

        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"boris@gmail.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Boris Gerard"))
                .andExpect(jsonPath("$.email").value("boris@gmail.com"))
                .andExpect(jsonPath("$.intakeAddress").value("track+boris2k4mp9@resistance.example"))
                .andReturn();

        assertEquals(7, result.getRequest().getSession()
                .getAttribute(LoginController.SESSION_ACCOUNT_ID));
    }

    @Test
    void loginWithBadCodeIsRejectedWithoutASession() throws Exception {
        when(otpService.verify(anyString(), anyString())).thenReturn(Optional.empty());

        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"boris@gmail.com\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_code"))
                .andReturn();

        assertEquals(null, result.getRequest().getSession()
                .getAttribute(LoginController.SESSION_ACCOUNT_ID));
    }

    @Test
    void meReturnsTheSessionAccount() throws Exception {
        when(accounts.findById(7)).thenReturn(Optional.of(boris));

        mockMvc.perform(get("/api/auth/me").sessionAttr(LoginController.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("boris@gmail.com"))
                .andExpect(jsonPath("$.intakeAddress").value("track+boris2k4mp9@resistance.example"));
    }

    @Test
    void meWithoutSessionIs401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        mockMvc.perform(post("/api/auth/logout").sessionAttr(LoginController.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().isNoContent());
    }
}
