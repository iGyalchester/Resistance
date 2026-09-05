package com.resistance.mvc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sends "/" to the dashboard.
 *
 * <p>It used to be a course demo: an applicant registration form with no
 * connection to the tracker, sitting on the one URL a visitor is most
 * likely to type. Anyone who reached the app's root got a form for a
 * feature that does not exist.
 *
 * <p>A view controller rather than a redirecting {@code @Controller}
 * method, because there is no logic here - and the security chain then
 * treats the redirect like any other request, so an anonymous visitor
 * lands on the login page and a signed-in one on their dashboard.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/dashboard");
    }
}
