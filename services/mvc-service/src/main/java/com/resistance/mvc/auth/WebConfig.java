package com.resistance.mvc.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final DashboardInterceptor dashboardInterceptor;

    public WebConfig(DashboardInterceptor dashboardInterceptor) {
        this.dashboardInterceptor = dashboardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dashboardInterceptor).addPathPatterns("/dashboard/**", "/dashboard");
    }
}
