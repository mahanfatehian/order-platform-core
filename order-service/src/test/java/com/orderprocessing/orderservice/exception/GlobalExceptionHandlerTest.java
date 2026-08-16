package com.orderprocessing.orderservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsCorrelatedSanitizedForbiddenResponseForMethodSecurityDenial() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/123/pack");
        request.addHeader("X-Correlation-Id", "correlation-forbidden-123");

        ResponseEntity<ApiError> response = handler.authorizationDenied(
                new AuthorizationDeniedException("ROLE_WAREHOUSE is required", new AuthorizationDecision(false)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).satisfies(body -> {
            assertThat(body.status()).isEqualTo(403);
            assertThat(body.code()).isEqualTo("FORBIDDEN");
            assertThat(body.message()).isEqualTo("You do not have permission to perform this operation");
            assertThat(body.message()).doesNotContain("ROLE_WAREHOUSE");
            assertThat(body.path()).isEqualTo("/api/orders/123/pack");
            assertThat(body.correlationId()).isEqualTo("correlation-forbidden-123");
        });
    }
}
