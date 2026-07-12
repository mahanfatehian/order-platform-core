package com.orderprocessing.userservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.security.web.ApiErrorResponse;
import com.orderprocessing.security.web.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_PATH_PATTERN = "/api/users/internal/**";

    private final InternalSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public InternalApiKeyFilter(InternalSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !pathMatcher.match(INTERNAL_PATH_PATTERN, request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String providedKey = request.getHeader(INTERNAL_HEADER);
        String expectedKey = properties.getApiKey();

        if (!matches(expectedKey, providedKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            String correlationId = CorrelationId.resolve(request);
            response.setHeader(CorrelationId.HEADER, correlationId);
            objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                    401,
                    "INVALID_INTERNAL_CREDENTIAL",
                    "Invalid or missing internal API key",
                    request.getRequestURI(),
                    correlationId
            ));
            return;
        }

        InternalApiKeyAuthenticationToken authentication =
                new InternalApiKeyAuthenticationToken("auth-service");

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean matches(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
