package com.orderprocessing.webui.service;

import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.dto.LoginTokens;
import com.orderprocessing.webui.exception.SessionExpiredException;
import com.orderprocessing.webui.form.LoginForm;
import com.orderprocessing.webui.model.UiAuthenticatedUser;
import com.orderprocessing.webui.model.UiSessionTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UiAuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(UiAuthenticationService.class);
    private final PlatformClient platformClient;
    private final SessionTokenService tokenService;
    private final JwtDecoder jwtDecoder;
    private final WebUiProperties properties;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public UiAuthenticationService(PlatformClient platformClient, SessionTokenService tokenService,
                                   JwtDecoder jwtDecoder, WebUiProperties properties) {
        this.platformClient = platformClient;
        this.tokenService = tokenService;
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
    }

    public UiAuthenticatedUser authenticate(LoginForm form, HttpServletRequest request, HttpServletResponse response) {
        LoginTokens responseTokens = platformClient.login(form.getUsername().trim(), form.getPassword());
        DecodedPair pair = decodePair(responseTokens);
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        tokenService.save(request, pair.tokens());
        Authentication authentication = authentication(pair.user());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        session.setMaxInactiveInterval((int) java.time.Duration.ofMinutes(30).toSeconds());
        return pair.user();
    }

    public UiSessionTokens refreshCurrentSession() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpSession session = attributes.getRequest().getSession(false);
        if (session == null) throw new SessionExpiredException("Your session expired", null);
        synchronized (session) {
            UiSessionTokens current = tokenService.current()
                    .orElseThrow(() -> new SessionExpiredException("Your session expired", null));
            Instant threshold = Instant.now().plus(properties.getSecurity().getRefreshSkew());
            if (!current.accessExpiresWithin(threshold)) return current;
            try {
                DecodedPair pair = decodePair(platformClient.refresh(current.refreshToken()));
                Authentication existing = SecurityContextHolder.getContext().getAuthentication();
                if (existing != null && existing.getPrincipal() instanceof UiAuthenticatedUser prior
                        && !prior.id().equals(pair.user().id())) {
                    throw new IllegalStateException("Refreshed identity changed");
                }
                tokenService.saveCurrent(pair.tokens());
                SecurityContextHolder.getContext().setAuthentication(authentication(pair.user()));
                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        SecurityContextHolder.getContext());
                return pair.tokens();
            } catch (RuntimeException exception) {
                expireCurrentSession();
                throw new SessionExpiredException("Your session expired. Please sign in again.", exception);
            }
        }
    }

    public void logoutCurrentSession() {
        tokenService.current().ifPresent(tokens -> {
            try {
                platformClient.logout(tokens.accessToken());
            } catch (RuntimeException exception) {
                log.warn("Backend token revocation did not complete during local logout: {}",
                        exception.getClass().getSimpleName());
            }
        });
        tokenService.clearCurrent();
    }

    public void expireCurrentSession() {
        tokenService.clearCurrent();
        SecurityContextHolder.clearContext();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpSession session = attributes.getRequest().getSession(false);
            if (session != null) session.invalidate();
        }
    }

    private DecodedPair decodePair(LoginTokens pair) {
        if (pair == null || pair.accessToken() == null || pair.refreshToken() == null) {
            throw new IllegalArgumentException("Authentication service returned an incomplete token pair");
        }
        Jwt access = jwtDecoder.decode(pair.accessToken());
        Jwt refresh = jwtDecoder.decode(pair.refreshToken());
        requireType(access, "access");
        requireType(refresh, "refresh");
        UUID userId = UUID.fromString(String.valueOf(access.getClaims().get("userId")));
        List<String> rawRoles = access.getClaimAsStringList("roles");
        Set<String> roles = new LinkedHashSet<>(rawRoles == null ? List.of() : rawRoles);
        UiAuthenticatedUser user = new UiAuthenticatedUser(userId, access.getSubject(), roles);
        UiSessionTokens tokens = new UiSessionTokens(pair.accessToken(), pair.refreshToken(),
                access.getExpiresAt(), refresh.getExpiresAt());
        return new DecodedPair(user, tokens);
    }

    private static void requireType(Jwt jwt, String expected) {
        if (!expected.equals(jwt.getClaimAsString("type"))) {
            throw new IllegalArgumentException("Unexpected token type");
        }
    }

    private static Authentication authentication(UiAuthenticatedUser user) {
        List<SimpleGrantedAuthority> authorities = user.roles().stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new).toList();
        return UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
    }

    private record DecodedPair(UiAuthenticatedUser user, UiSessionTokens tokens) { }
}
