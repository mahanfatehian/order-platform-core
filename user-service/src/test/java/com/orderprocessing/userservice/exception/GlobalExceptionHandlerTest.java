package com.orderprocessing.userservice.exception;

import com.orderprocessing.security.web.ApiErrorResponse;
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

    @Test
    void aWrongCurrentPasswordIsARejectedFieldRatherThanARejectedIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/me/change-password");
        request.addHeader("X-Correlation-Id", "correlation-change-password");

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidCurrentPassword(
                new InvalidCurrentPasswordException("Current password is incorrect"), request);

        // 401 would tell the caller its access token is finished; the BFF acts on that by ending the session,
        // so a mistyped password would sign the user out instead of showing an error on the form.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).satisfies(body -> {
            assertThat(body.status()).isEqualTo(400);
            assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
            assertThat(body.fieldErrors()).containsEntry("currentPassword", "Current password is incorrect");
            assertThat(body.correlationId()).isEqualTo("correlation-change-password");
        });
    }

    @Test
    void theWrongCurrentPasswordIsNotRoutedToTheAuthenticationHandler() {
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

        Method resolved = resolver.resolveMethod(new InvalidCurrentPasswordException("Current password is incorrect"));

        assertThat(resolved).isNotNull();
        assertThat(resolved.getName()).isEqualTo("handleInvalidCurrentPassword");
    }

    @Test
    void aRealCredentialFailureStillAnswers401() {
        // The internal authenticate endpoint depends on this: auth-service reads a 401 from it as bad credentials.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/internal/authenticate");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthentication(
                new AuthenticationFailedException("Invalid username or password"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
