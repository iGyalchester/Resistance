package com.resistance.mvc.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Who may call what. The React shell (index.html and its assets) is
 * public: it contains nothing but the app's code, and the app decides
 * on its own whether to show the login screen. Everything that carries
 * data is under /api/** and needs an authenticated session, established
 * by the OTP flow in AuthApiController through SessionAuthenticator;
 * /api/admin/** needs the ADMIN role on top. Anonymous and forbidden
 * calls get JSON bodies, never an HTML redirect - the client turns them
 * into its own navigation.
 *
 * CSRF stays on with a token the JavaScript can read (see
 * SpaCsrfTokenRequestHandler). HSTS is opt-in per profile
 * (tracker.security.hsts): behind the AWS load balancer the qa profile
 * turns it on, the plain-http dev profile leaves it off.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository,
                                           @Value("${tracker.security.hsts:false}") boolean hsts) throws Exception {

        http.authorizeHttpRequests(configurer ->
                configurer
                        .requestMatchers("/api/auth/code", "/api/auth/login", "/api/help").permitAll()
                        // the ops view: signed in is not enough, the account must be on the admin list
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // the load balancer's health check (reports only UP/DOWN), the
                        // shell, its assets, and the app's own routes
                        .anyRequest().permitAll()
        );

        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()));

        AuthenticationEntryPoint api401 = (request, response, exception) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"unauthenticated\"}");
        };
        AccessDeniedHandler api403 = (request, response, exception) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"forbidden\"}");
        };
        http.exceptionHandling(handling -> handling
                .authenticationEntryPoint(api401)
                .accessDeniedHandler(api403));

        // Strict-Transport-Security tells the browser to insist on https for
        // a year. Sent only on https requests (behind the ALB that means the
        // forwarded scheme) and only when the profile asks for it.
        http.headers(headers -> headers.httpStrictTransportSecurity(policy -> {
            if (hsts) {
                policy.maxAgeInSeconds(31_536_000).includeSubDomains(true);
            } else {
                policy.disable();
            }
        }));

        // our controllers do login/logout themselves
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());
        http.logout(logout -> logout.disable());

        http.securityContext(context -> context.securityContextRepository(securityContextRepository));

        return http.build();
    }
}
