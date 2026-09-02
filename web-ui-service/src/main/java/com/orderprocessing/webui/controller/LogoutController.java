package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.service.UiAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Duration;

@Controller
public class LogoutController {
    private final UiAuthenticationService authenticationService;

    public LogoutController(UiAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logoutCurrentSession();

        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        ResponseCookie expiredSession = ResponseCookie.from("ORDER_PLATFORM_SESSION", "")
                .path("/")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredSession.toString());
        return "redirect:/login?logout";
    }
}
