package com.resistance.mvc.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Everything except the login flow requires an authenticated session.
 * Authentication itself is our OTP flow (LoginController stores the
 * authenticated context via the SecurityContextRepository below); CSRF
 * protection stays at its secure default - Thymeleaf injects the token
 * into every th:action form automatically.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository) throws Exception {

        http.authorizeHttpRequests(configurer ->
                configurer
                        .requestMatchers("/login", "/login/**", "/logout", "/css/**", "/error").permitAll()
                        .anyRequest().authenticated()
        );

        // anonymous visitors go to the OTP login page instead of a bare 403
        http.exceptionHandling(handling ->
                handling.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));

        // our controllers do login/logout themselves
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());
        http.logout(logout -> logout.disable());

        http.securityContext(context -> context.securityContextRepository(securityContextRepository));

        return http.build();
    }
}
