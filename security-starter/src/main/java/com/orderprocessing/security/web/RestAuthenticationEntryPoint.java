package com.orderprocessing.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String correlationId = CorrelationId.resolve(request);
        response.setHeader(CorrelationId.HEADER, correlationId);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                401,
                "AUTHENTICATION_REQUIRED",
                "Missing, invalid, expired or revoked access token",
                request.getRequestURI(),
                correlationId
        ));
    }
}
