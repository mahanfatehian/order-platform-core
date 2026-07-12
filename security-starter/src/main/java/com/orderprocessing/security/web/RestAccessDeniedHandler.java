package com.orderprocessing.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String correlationId = CorrelationId.resolve(request);
        response.setHeader(CorrelationId.HEADER, correlationId);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                403,
                "FORBIDDEN",
                "You do not have permission to access this resource",
                request.getRequestURI(),
                correlationId
        ));
    }
}
