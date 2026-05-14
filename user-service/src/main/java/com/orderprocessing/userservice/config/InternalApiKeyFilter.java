package com.orderprocessing.userservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_PATH_PATTERN = "/api/users/internal/**";

    private final InternalSecurityProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public InternalApiKeyFilter(InternalSecurityProperties properties) {
        this.properties = properties;
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

        if (expectedKey == null || expectedKey.isEmpty() || !expectedKey.equals(providedKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            response.getWriter().write(
                    "{\n" +
                            "  \"status\": 401,\n" +
                            "  \"error\": \"Unauthorized\",\n" +
                            "  \"message\": \"Invalid or missing internal API key\",\n" +
                            "  \"path\": \"" + request.getRequestURI() + "\"\n" +
                            "}"
            );
            return;
        }

        InternalApiKeyAuthenticationToken authentication =
                new InternalApiKeyAuthenticationToken("auth-service");

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}