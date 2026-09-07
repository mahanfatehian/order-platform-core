package com.orderprocessing.webui;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.dto.ProductView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class CartQuantityLimitMvcTest {
    private static final UUID PRODUCT = UUID.fromString("aaaaaaaa-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @MockBean AuthenticatedPlatformClient authenticatedClient;
    @MockBean PlatformClient platformClient;

    @BeforeEach
    void productIsOrderable() {
        when(authenticatedClient.product(any())).thenReturn(new ProductView(
                PRODUCT, "Product", "description", "SKU-1", new BigDecimal("10.00"), "OTHER",
                true, 50, Instant.now(), Instant.now()));
    }

    @Test
    void addingAboveThePerProductCeilingIsRejectedNotServerError() throws Exception {
        // The configured ceiling is 99. CartService throws IllegalArgumentException above it, and nothing maps
        // that exception, so without a check here the shopper gets an error page instead of a message.
        mvc.perform(post("/app/cart/items").with(user("customer").roles("USER")).with(csrf())
                        .param("productId", PRODUCT.toString())
                        .param("quantity", "100"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void changingQuantityAboveTheCeilingIsRejectedNotServerError() throws Exception {
        mvc.perform(post("/app/cart/items/" + PRODUCT + "/quantity")
                        .with(user("customer").roles("USER")).with(csrf())
                        .param("quantity", "100"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void theCeilingItselfIsStillAccepted() throws Exception {
        mvc.perform(post("/app/cart/items").with(user("customer").roles("USER")).with(csrf())
                        .param("productId", PRODUCT.toString())
                        .param("quantity", "99"))
                .andExpect(status().is3xxRedirection());
    }
}
