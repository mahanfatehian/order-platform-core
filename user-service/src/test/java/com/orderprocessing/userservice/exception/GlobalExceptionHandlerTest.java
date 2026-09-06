package com.orderprocessing.userservice.exception;

import com.orderprocessing.security.web.ApiErrorResponse;
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
    void methodSecurityDenialIsForbiddenRatherThanAnInternalError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/admin");
        request.addHeader("X-Correlation-Id", "correlation-forbidden-2");

        ResponseEntity<ApiErrorResponse> response = handler.authorizationDenied(
                new AuthorizationDeniedException("ROLE_ADMIN is required", new AuthorizationDecision(false)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).satisfies(body -> {
            assertThat(body.status()).isEqualTo(403);
            assertThat(body.code()).isEqualTo("FORBIDDEN");
            // The denial reason names the required authority; it must not reach the caller.
            assertThat(body.message()).doesNotContain("ROLE_ADMIN");
            assertThat(body.path()).isEqualTo("/api/users/admin");
            assertThat(body.correlationId()).isEqualTo("correlation-forbidden-2");
        });
    }
}
