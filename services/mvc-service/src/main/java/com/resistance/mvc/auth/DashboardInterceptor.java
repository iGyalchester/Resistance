package com.resistance.mvc.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Sends anonymous visitors of /dashboard/** to the login page.
 */
@Component
public class DashboardInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(LoginController.SESSION_ACCOUNT_ID) == null) {
            response.sendRedirect("/login");
            return false;
        }
        return true;
    }
}
