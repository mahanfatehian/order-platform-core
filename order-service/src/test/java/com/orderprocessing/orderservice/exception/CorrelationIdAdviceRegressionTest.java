package com.orderprocessing.orderservice.exception;

import com.orderprocessing.security.web.CorrelationId;
import com.orderprocessing.security.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdAdviceRegressionTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "unsafe correlation id")
    void filterAndAdviceUseTheSameSafeCanonicalCorrelationId(String inboundCorrelationId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/123/pack");
        if (inboundCorrelationId != null) {
            request.addHeader(CorrelationId.HEADER, inboundCorrelationId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ApiError> body = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> body.set(
                handler.authorizationDenied(
                        new AuthorizationDeniedException("ROLE_WAREHOUSE is required", new AuthorizationDecision(false)),
                        (HttpServletRequest) servletRequest
                ).getBody()
        ));

        String canonicalId = response.getHeader(CorrelationId.HEADER);
        assertThat(canonicalId).matches("[A-Za-z0-9._-]{1,128}");
        assertThat(body.get().correlationId()).isEqualTo(canonicalId);
        assertThat(body.get().correlationId()).isNotEqualTo(inboundCorrelationId);
    }
}
