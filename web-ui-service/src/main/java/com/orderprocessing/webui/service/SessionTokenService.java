package com.orderprocessing.webui.service;

import com.orderprocessing.webui.model.UiSessionTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Service
public class SessionTokenService {
    private static final String TOKENS = SessionTokenService.class.getName() + ".TOKENS";

    public Optional<UiSessionTokens> current() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return Optional.empty();
        HttpSession session = attributes.getRequest().getSession(false);
        return session == null ? Optional.empty() : Optional.ofNullable((UiSessionTokens) session.getAttribute(TOKENS));
    }

    public void save(HttpServletRequest request, UiSessionTokens tokens) {
        request.getSession(true).setAttribute(TOKENS, tokens);
    }

    public void saveCurrent(UiSessionTokens tokens) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        attributes.getRequest().getSession(true).setAttribute(TOKENS, tokens);
    }

    public void clearCurrent() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpSession session = attributes.getRequest().getSession(false);
            if (session != null) session.removeAttribute(TOKENS);
        }
    }
}
