package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.exception.BackendClientException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The service-unavailable page renders retryPath as a "Try again" anchor, which the browser follows with a GET.
 * Replaying the URI of a failed POST that way is not a retry: the cart mutations sit on sub-paths with no GET
 * mapping, so it answers with an error rather than the page the shopper wanted.
 */
class PageExceptionHandlerRetryPathTest {
    private final PageExceptionHandler handler = new PageExceptionHandler();

    private static BackendClientException backendFailure() {
        return new BackendClientException(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "down", Map.of());
    }

    @Test
    void aFailedGetKeepsItsPathSoTheLinkActuallyRetries() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/orders");

        ModelAndView view = handler.backend(backendFailure(), request);

        assertThat(view.getModel().get("retryPath")).isEqualTo("/app/orders");
    }

    @Test
    void aFailedPostOffersNoPathRatherThanOneThatCannotBeReplayed() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/app/cart/items/abc/remove");

        ModelAndView view = handler.backend(backendFailure(), request);

        // Null lets the template fall back to the workspace root instead of linking at a POST-only path.
        assertThat(view.getModel().get("retryPath")).isNull();
    }

    @Test
    void theTransportFailurePageFollowsTheSameRule() {
        ModelAndView get = handler.unavailable(new ResourceAccessException("down"),
                new MockHttpServletRequest("GET", "/app/cart"));
        ModelAndView post = handler.unavailable(new ResourceAccessException("down"),
                new MockHttpServletRequest("POST", "/app/cart/clear"));

        assertThat(get.getModel().get("retryPath")).isEqualTo("/app/cart");
        assertThat(post.getModel().get("retryPath")).isNull();
    }
}
