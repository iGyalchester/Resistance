package com.resistance.mvc.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

/**
 * Everything except the login flow requires an authenticated session.
 * Authentication itself is our OTP flow (LoginController and
 * AuthApiController store the authenticated context via
 * SessionAuthenticator). Two clients share this chain: the Thymeleaf
 * pages and the React app. CSRF tokens therefore live in a cookie the
 * React app can read (see SpaCsrfTokenRequestHandler) - Thymeleaf still
 * injects the request-attribute token into every th:action form.
 * Anonymous browsers get redirected to /login; anonymous /api/** calls
 * get a 401 JSON body instead, which the React client turns into its
 * own login redirect.
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
                        .requestMatchers("/api/auth/code", "/api/auth/login").permitAll()
                        .anyRequest().authenticated()
        );

        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()));

        AuthenticationEntryPoint api401 = (request, response, exception) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"unauthenticated\"}");
        };
        http.exceptionHandling(handling -> handling
                .defaultAuthenticationEntryPointFor(api401,
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                        AnyRequestMatcher.INSTANCE));

        // our controllers do login/logout themselves
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());
        http.logout(logout -> logout.disable());

        http.securityContext(context -> context.securityContextRepository(securityContextRepository));

        return http.build();
    }
}
