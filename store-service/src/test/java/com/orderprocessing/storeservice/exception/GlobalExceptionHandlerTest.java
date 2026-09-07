package com.orderprocessing.storeservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import java.lang.reflect.Method;

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
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/store/admin/products");
        request.addHeader("X-Correlation-Id", "correlation-forbidden-1");

        ResponseEntity<ApiError> response = handler.authorizationDenied(
                new AuthorizationDeniedException("ROLE_ADMIN is required", new AuthorizationDecision(false)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).satisfies(body -> {
            assertThat(body.status()).isEqualTo(403);
            assertThat(body.code()).isEqualTo("FORBIDDEN");
            // The denial reason names the required authority; it must not reach the caller.
            assertThat(body.message()).doesNotContain("ROLE_ADMIN");
            assertThat(body.path()).isEqualTo("/api/store/admin/products");
            assertThat(body.correlationId()).isEqualTo("correlation-forbidden-1");
        });
    }

    @Test
    void theDenialIsRoutedToThatHandlerRatherThanTheCatchAll() {
        // Calling the method directly proves only what the method does. This resolves the exception the way
        // Spring does, so dropping @ExceptionHandler would fall through to the catch-all and fail here.
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

        Method resolved = resolver.resolveMethod(
                new AuthorizationDeniedException("ROLE_ADMIN is required", new AuthorizationDecision(false)));

        assertThat(resolved).isNotNull();
        assertThat(resolved.getName()).isEqualTo("authorizationDenied");
    }
}
