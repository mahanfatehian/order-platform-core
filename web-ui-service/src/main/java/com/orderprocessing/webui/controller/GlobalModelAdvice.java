package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.model.UiAuthenticatedUser;
import com.orderprocessing.webui.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {
    private final CartService cartService;
    private final WebUiProperties properties;

    public GlobalModelAdvice(CartService cartService, WebUiProperties properties) {
        this.cartService = cartService;
        this.properties = properties;
    }

    @ModelAttribute("cartCount")
    public int cartCount(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        return session != null && authentication != null && authentication.isAuthenticated() ? cartService.count(session) : 0;
    }

    @ModelAttribute("currentUser")
    public UiAuthenticatedUser currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return null;
        if (authentication.getPrincipal() instanceof UiAuthenticatedUser user) return user;
        java.util.Set<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new UiAuthenticatedUser(null, authentication.getName(), roles);
    }

    @ModelAttribute("registrationEnabled")
    public boolean registrationEnabled() { return properties.getFeatures().isRegistrationEnabled(); }

    @ModelAttribute("demoMode")
    public boolean demoMode() { return properties.getFeatures().isDemoMode(); }

    @ModelAttribute("cartMaximumQuantity")
    public int cartMaximumQuantity() { return properties.getCart().getMaximumQuantity(); }
}
