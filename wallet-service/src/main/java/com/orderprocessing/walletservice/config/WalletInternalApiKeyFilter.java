package com.orderprocessing.walletservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WalletInternalApiKeyFilter extends OncePerRequestFilter {
    static final String INTERNAL_HEADER = "X-Wallet-Internal-Api-Key";
    private static final String INTERNAL_PATH_PATTERN = "/api/wallets/internal/**";
    private final WalletInternalSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public WalletInternalApiKeyFilter(WalletInternalSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !pathMatcher.match(INTERNAL_PATH_PATTERN, request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!matches(properties.getApiKey(), request.getHeader(INTERNAL_HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", OffsetDateTime.now());
            body.put("status", 401);
            body.put("code", "INVALID_INTERNAL_API_KEY");
            body.put("message", "Invalid or missing wallet internal API key");
            body.put("path", request.getRequestURI());
            String correlationId = request.getHeader("X-Correlation-Id");
            body.put("correlationId", correlationId == null || correlationId.isBlank()
                    ? UUID.randomUUID().toString() : correlationId);
            body.put("fieldErrors", Map.of());
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "wallet-internal-client", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
        filterChain.doFilter(request, response);
    }

    private boolean matches(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
