package com.orderprocessing.webui;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.form.ProductForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
class AdminProductFormMvcTest {
    @Autowired MockMvc mvc;
    @MockBean AuthenticatedPlatformClient client;
    @MockBean PlatformClient platformClient;

    @BeforeEach
    void backendRejectsOverPrecisionPrices() {
        // What store-service does with a price outside @Digits(integer = 8, fraction = 2).
        doThrow(new BackendClientException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid price", Map.of()))
                .when(client).createProduct(any(ProductForm.class));
    }

    @Test
    void anOverLongPriceIsAFieldErrorOnTheFormNotAServiceUnavailablePage() throws Exception {
        mvc.perform(post("/admin/products").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "Widget").param("sku", "SKU-1").param("description", "d")
                        .param("category", "OTHER").param("price", "1234567890.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/form"));

        // Caught by binding, so the request never reaches the backend that would have failed it.
        verify(client, never()).createProduct(any());
    }

    @Test
    void tooManyDecimalPlacesIsAlsoCaughtBeforeTheBackend() throws Exception {
        mvc.perform(post("/admin/products").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "Widget").param("sku", "SKU-1").param("description", "d")
                        .param("category", "OTHER").param("price", "10.005"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/form"));

        verify(client, never()).createProduct(any());
    }
}
