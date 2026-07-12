package com.orderprocessing.webui;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.dto.*;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.service.UiAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class WebUiMvcTest {
    private static final UUID PRODUCT_ID = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("bbbbbbbb-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("cccccccc-1111-1111-1111-111111111111");
    @Autowired MockMvc mvc;
    @MockBean AuthenticatedPlatformClient authenticatedClient;
    @MockBean PlatformClient platformClient;
    @MockBean UiAuthenticationService authenticationService;

    @BeforeEach
    void defaults() {
        Instant now = Instant.parse("2026-01-02T03:04:05Z");
        ProductView product = new ProductView(PRODUCT_ID, "Signal Lamp", "A test product", "SIG-001",
                new BigDecimal("29.90"), "OTHER", true, 12, now, now);
        QuoteItemView quoteItem = new QuoteItemView(PRODUCT_ID, product.name(), product.price(), true, 2, 12, true);
        CartView cart = new CartView(List.of(quoteItem), new BigDecimal("59.80"), true);
        OrderItemView orderItem = new OrderItemView(UUID.randomUUID(), PRODUCT_ID, product.name(), product.price(), 2,
                new BigDecimal("59.80"));
        OrderView order = new OrderView(ORDER_ID, USER_ID, "PENDING", new BigDecimal("59.80"), null,
                List.of(orderItem), now, now, true);
        UserView userView = new UserView(USER_ID, "customer", "customer@example.test", "Casey", "Customer",
                true, true, Set.of("ROLE_USER"), now, now);
        InventoryView inventoryView = new InventoryView(PRODUCT_ID, product.name(), product.sku(), 20, 8, 12, now);

        when(authenticatedClient.products(anyInt(), anyInt(), anyString(), anyString(), anyBoolean()))
                .thenReturn(PageResponse.empty(0, 12));
        when(authenticatedClient.product(any())).thenReturn(product);
        when(authenticatedClient.quote(anyMap())).thenReturn(cart);
        when(authenticatedClient.myOrders(anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(order), 0, 10, 1, 1, true, true));
        when(authenticatedClient.order(any())).thenReturn(order);
        when(authenticatedClient.profile()).thenReturn(userView);
        when(authenticatedClient.adminUser(any())).thenReturn(userView);
        when(authenticatedClient.adminUsers(anyInt(), anyInt(), anyString())).thenReturn(new PageResponse<>(List.of(userView), 0, 20, 1, 1, true, true));
        when(authenticatedClient.adminProducts(anyInt(), anyInt(), anyString())).thenReturn(new PageResponse<>(List.of(product), 0, 20, 1, 1, true, true));
        when(authenticatedClient.inventory(anyInt(), anyInt(), anyString())).thenReturn(new PageResponse<>(List.of(inventoryView), 0, 20, 1, 1, true, true));
        when(authenticatedClient.adminOrders(anyInt(), anyInt(), anyString(), anyString())).thenReturn(new PageResponse<>(List.of(order), 0, 20, 1, 1, true, true));
        when(authenticatedClient.serviceHealth()).thenReturn(List.of());
    }

    @Test
    void unauthenticatedApplicationRequestRedirectsToLogin() throws Exception {
        mvc.perform(get("/app")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginTemplateRendersWithoutExposingBrowserTokenStorage() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("ORDER_PLATFORM_SESSION"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign in securely")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("localStorage"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("accessToken"))));
    }

    @Test
    void loginMutationRequiresCsrf() throws Exception {
        mvc.perform(post("/login").param("username", "customer").param("password", "password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutExplicitlyExpiresTheBrowserSessionCookie() throws Exception {
        mvc.perform(post("/logout").with(user("customer").roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(cookie().maxAge("ORDER_PLATFORM_SESSION", 0));
    }

    @Test
    void customerCannotOpenAdminPages() throws Exception {
        mvc.perform(get("/admin").with(user("customer").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void backendOwnershipDenialRendersTheSanitizedForbiddenPage() throws Exception {
        UUID foreignOrderId = UUID.fromString("dddddddd-1111-1111-1111-111111111111");
        when(authenticatedClient.order(foreignOrderId)).thenThrow(new BackendClientException(
                HttpStatus.FORBIDDEN, "FORBIDDEN", "Order belongs to another user", Map.of()));

        mvc.perform(get("/app/orders/{id}", foreignOrderId).with(user("customer").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("This workspace is restricted")));
    }

    @Test
    void authenticatedDashboardRendersWhenBackendsAreAvailable() throws Exception {
        mvc.perform(get("/app").with(user("customer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Welcome back, customer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Recent orders")));
    }

    @Test
    void adminDashboardRendersForAdmin() throws Exception {
        mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Platform administration")));
    }

    @Test
    void catalogHtmxRequestReturnsOnlyGridFragment() throws Exception {
        mvc.perform(get("/app/catalog").header("HX-Request", "true").with(user("customer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No matching products")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Product catalog</h1>"))));
    }

    @Test
    void customerTemplatesRenderWithServerData() throws Exception {
        var customer = user("customer").roles("USER");
        mvc.perform(get("/app/catalog/{id}", PRODUCT_ID).with(customer)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Signal Lamp")));
        mvc.perform(get("/app/orders").with(customer)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("My orders")));
        mvc.perform(get("/app/orders/{id}", ORDER_ID).with(customer)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Checking every 3 seconds")));
        mvc.perform(get("/app/profile").with(customer)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("customer@example.test")));
    }

    @Test
    void cartAndCheckoutWorkAsNormalFormsWithoutHtmx() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/app/cart/items").with(user("customer").roles("USER")).with(csrf()).session(session)
                        .param("productId", PRODUCT_ID.toString()).param("quantity", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/app/cart"));
        mvc.perform(get("/app/cart").with(user("customer").roles("USER")).session(session))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Order estimate")));
        mvc.perform(get("/app/checkout").with(user("customer").roles("USER")).session(session))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm your order")));
    }

    @Test
    void administratorTemplatesRenderWithSanitizedData() throws Exception {
        var admin = user("admin").roles("ADMIN");
        mvc.perform(get("/admin/products").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/products/new").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/products/{id}/edit", PRODUCT_ID).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/inventory").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/orders").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/orders/{id}", ORDER_ID).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/users").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/users/{id}", USER_ID).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/system").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("never environment values")));
    }
}
