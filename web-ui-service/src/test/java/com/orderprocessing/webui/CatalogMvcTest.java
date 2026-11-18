package com.orderprocessing.webui;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.dto.PageResponse;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class CatalogMvcTest {
    private static final UUID PRODUCT_ID = UUID.fromString("dddddddd-1111-1111-1111-111111111111");
    private static final UUID PICTURE_ID = UUID.fromString("eeeeeeee-1111-1111-1111-111111111111");

    @Autowired MockMvc mvc;
    @MockBean AuthenticatedPlatformClient client;
    @MockBean PlatformClient platformClient;

    @BeforeEach
    void catalogHasAStoredPicture() {
        Instant now = Instant.parse("2026-01-02T03:04:05Z");
        ProductView product = new ProductView(PRODUCT_ID, "Desk Lamp", "A lamp", "LAMP-1",
                new BigDecimal("42.00"), "Lamps", true, 4, now, now,
                UUID.fromString("c1000000-0000-4000-8000-000000000099"), PICTURE_ID);
        when(client.products(anyInt(), anyInt(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new PageResponse<>(List.of(product), 0, 12, 1, 1, true, true));
        when(client.product(any())).thenReturn(product);
    }

    @Test
    void catalogPagesShowTheCategoryNameAndTheStoredPicture() throws Exception {
        String picturePath = "/app/catalog/" + PRODUCT_ID + "/pictures/" + PICTURE_ID;
        mvc.perform(get("/app/catalog").with(user("customer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lamps")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(picturePath)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("src=\"http"))));
        mvc.perform(get("/app/catalog/{id}", PRODUCT_ID).with(user("customer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lamps")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(picturePath)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("src=\"http"))));
    }
}
