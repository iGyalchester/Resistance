package com.resistance.mvc.auth;

import com.resistance.mvc.auth.LoginController;
import com.resistance.shared.models.entity.UserAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionAuthenticatorTests {

    private final SecurityContextRepository repository = mock(SecurityContextRepository.class);
    private final AdminRoles admins = new AdminRoles(" Boris@Example.com , ops@example.com ");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static UserAccount account(String email) {
        UserAccount account = new UserAccount("Someone", email);
        account.setId(7);
        return account;
    }

    @Test
    void listedEmailGetsAdminOnTopOfUserCaseInsensitively() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SessionAuthenticator(repository, admins).establish(account("boris@example.com"), request, response);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThat(request.getSession(false).getAttribute(LoginController.SESSION_ACCOUNT_ID)).isEqualTo(7);
        verify(repository).saveContext(any(), any(), any());
    }

    @Test
    void anyoneElseIsJustAUser() {
        new SessionAuthenticator(repository, admins).establish(account("guest@example.com"),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void blankListMeansNoAdminsAtAll() {
        assertThat(new AdminRoles("").isAdmin("boris@example.com")).isFalse();
        assertThat(new AdminRoles(null).isAdmin("boris@example.com")).isFalse();
        assertThat(new AdminRoles(" , ").isAdmin("")).isFalse();
        assertThat(admins.isAdmin(null)).isFalse();
    }
}
