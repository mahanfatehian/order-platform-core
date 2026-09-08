package com.orderprocessing.webui;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.dto.CartView;
import com.orderprocessing.webui.dto.ProductView;
import com.orderprocessing.webui.dto.QuoteItemView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.session.store-type=none",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "app.security.jwt-secret=test-only-jwt-secret-that-is-long-enough-012345678901234567890123456789",
        "app.services.auth-url=http://localhost:18081",
        "app.services.user-url=http://localhost:18082",
        "app.services.store-url=http://localhost:18083",
        "app.services.order-url=http://localhost:18084",
        "app.services.store-internal-api-key=test-only-store-internal-key-0123456789",
        "spring.data.redis.password=test-only"
})
@AutoConfigureMockMvc
class CheckoutIdempotencyMvcTest {
    private static final UUID PRODUCT = UUID.fromString("bbbbbbbb-3333-3333-3333-333333333333");

    @Autowired MockMvc mvc;
    @MockBean AuthenticatedPlatformClient client;
    @MockBean PlatformClient platformClient;

    private final RequestPostProcessor customer = user("customer").roles("USER");

    @BeforeEach
    void cartIsOrderable() {
        when(client.product(any())).thenReturn(new ProductView(
                PRODUCT, "Lamp", "d", "SKU-9", new BigDecimal("10.00"), "OTHER",
                true, 50, Instant.now(), Instant.now()));
        when(client.quote(anyMap())).thenReturn(new CartView(
                List.of(new QuoteItemView(PRODUCT, "Lamp", new BigDecimal("10.00"), true, 2, 50, true)),
                new BigDecimal("20.00"), true));
    }

    private MockHttpSession sessionWithCart() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/app/cart/items").with(customer).with(csrf()).session(session)
                .param("productId", PRODUCT.toString()).param("quantity", "2"));
        return session;
    }

    private String renderReviewAndReadKey(MockHttpSession session) throws Exception {
        return (String) mvc.perform(get("/app/checkout").with(customer).session(session))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("idempotencyKey");
    }

    @Test
    void reviewKeepsTheSameKeyWhileTheCartIsUnchanged() throws Exception {
        MockHttpSession session = sessionWithCart();

        String first = renderReviewAndReadKey(session);
        String second = renderReviewAndReadKey(session);

        // A new key on every render means order-service cannot recognise a retry as the same intent.
        assertThat(first).isNotNull();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void aLostResponseLeavesTheKeyIntactSoTheRetryIsRecognisedAsTheSameOrder() throws Exception {
        MockHttpSession session = sessionWithCart();
        String key = renderReviewAndReadKey(session);
        // order-service commits, then the response is lost. Only a 409 is handled, so this propagates and the
        // cart and key are both left in place.
        when(client.createOrder(anyMap(), anyString()))
                .thenThrow(new ResourceAccessException("read timed out"));

        mvc.perform(post("/app/checkout").with(customer).with(csrf()).session(session)
                        .param("idempotencyKey", key))
                .andExpect(status().isServiceUnavailable());

        // The shopper reloads the review page and orders again. It must carry the ORIGINAL key, or order-service
        // sees fresh intent and writes a second order for a cart that was already placed.
        assertThat(renderReviewAndReadKey(session)).isEqualTo(key);
    }

    @Test
    void aChangedCartStillEarnsAFreshKey() throws Exception {
        MockHttpSession session = sessionWithCart();
        String first = renderReviewAndReadKey(session);

        mvc.perform(post("/app/cart/items/{id}/quantity", PRODUCT).with(customer).with(csrf()).session(session)
                .param("quantity", "5"));

        // Different intent, so reusing the key would make order-service reject it as an idempotency conflict.
        assertThat(renderReviewAndReadKey(session)).isNotEqualTo(first);
    }
}
