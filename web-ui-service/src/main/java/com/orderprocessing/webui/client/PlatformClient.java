package com.orderprocessing.webui.client;

import com.orderprocessing.webui.config.WebUiProperties;
import com.netflix.discovery.EurekaClient;
import com.orderprocessing.webui.dto.*;
import com.orderprocessing.webui.form.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class PlatformClient {
    private final RestClient auth;
    private final RestClient users;
    private final RestClient store;
    private final RestClient orders;
    private final String storeInternalApiKey;
    private final ObjectProvider<EurekaClient> eurekaClient;

    public PlatformClient(RestClient.Builder builder, WebUiProperties properties,
                          ObjectProvider<EurekaClient> eurekaClient) {
        this.auth = builder.clone().baseUrl(properties.getServices().getAuthUrl()).build();
        this.users = builder.clone().baseUrl(properties.getServices().getUserUrl()).build();
        this.store = builder.clone().baseUrl(properties.getServices().getStoreUrl()).build();
        this.orders = builder.clone().baseUrl(properties.getServices().getOrderUrl()).build();
        this.storeInternalApiKey = properties.getServices().getStoreInternalApiKey();
        this.eurekaClient = eurekaClient;
    }

    public LoginTokens login(String username, String password) {
        return auth.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password)).retrieve().body(LoginTokens.class);
    }

    public LoginTokens refresh(String refreshToken) {
        return auth.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken)).retrieve().body(LoginTokens.class);
    }

    public void logout(String accessToken) {
        auth.post().uri("/api/auth/logout").headers(headers -> bearer(headers, accessToken))
                .retrieve().toBodilessEntity();
    }

    public UserView register(RegistrationForm form) {
        return users.post().uri("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", form.getUsername(), "email", form.getEmail(),
                        "firstName", nullToEmpty(form.getFirstName()), "lastName", nullToEmpty(form.getLastName()),
                        "password", form.getPassword()))
                .retrieve().body(UserView.class);
    }

    public PageResponse<ProductView> products(String token, int page, int size, String query, String sort, boolean inStock) {
        String uri = UriComponentsBuilder.fromPath("/api/store/products")
                .queryParam("page", page).queryParam("size", size).queryParam("q", nullToEmpty(query))
                .queryParam("sort", sort).queryParamIfPresent("inStock", inStock ? java.util.Optional.of(true) : java.util.Optional.empty())
                .build().encode().toUriString();
        return store.get().uri(uri).headers(headers -> bearer(headers, token)).retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    public ProductView product(String token, UUID id) {
        return store.get().uri("/api/store/products/{id}", id).headers(headers -> bearer(headers, token))
                .retrieve().body(ProductView.class);
    }

    public CartView quote(String token, Map<UUID, Integer> quantities) {
        List<Map<String, Object>> items = quantities.entrySet().stream()
                .map(entry -> Map.<String, Object>of("productId", entry.getKey(), "quantity", entry.getValue())).toList();
        QuoteResponse response = store.post().uri("/api/store/internal/products/quote").headers(headers -> {
                    bearer(headers, token);
                    headers.set("X-Store-Internal-Api-Key", storeInternalApiKey);
                })
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("items", items)).retrieve().body(QuoteResponse.class);
        List<QuoteItemView> quoted = response == null || response.items() == null ? List.of() : response.items();
        java.math.BigDecimal total = quoted.stream().map(QuoteItemView::subtotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        boolean ready = !quoted.isEmpty() && quoted.size() == quantities.size()
                && quoted.stream().allMatch(QuoteItemView::active);
        return new CartView(quoted, total, ready);
    }

    public OrderView createOrder(String token, Map<UUID, Integer> quantities, String idempotencyKey) {
        List<Map<String, Object>> items = quantities.entrySet().stream()
                .map(entry -> Map.<String, Object>of("productId", entry.getKey(), "quantity", entry.getValue())).toList();
        return orders.post().uri("/api/orders").headers(headers -> {
                    bearer(headers, token);
                    headers.set("Idempotency-Key", idempotencyKey);
                }).contentType(MediaType.APPLICATION_JSON).body(Map.of("items", items)).retrieve().body(OrderView.class);
    }

    public PageResponse<OrderView> myOrders(String token, int page, int size) {
        return orders.get().uri(uri("/api/orders/my-orders", page, size, Map.of()))
                .headers(headers -> bearer(headers, token)).retrieve().body(new ParameterizedTypeReference<>() { });
    }

    public OrderView order(String token, UUID id) {
        return orders.get().uri("/api/orders/{id}", id).headers(headers -> bearer(headers, token))
                .retrieve().body(OrderView.class);
    }

    public OrderView cancelOrder(String token, UUID id) {
        return orders.post().uri("/api/orders/{id}/cancel", id).headers(headers -> bearer(headers, token))
                .retrieve().body(OrderView.class);
    }

    public UserView profile(String token) {
        return users.get().uri("/api/users/me").headers(headers -> bearer(headers, token)).retrieve().body(UserView.class);
    }

    public UserView updateProfile(String token, ProfileForm form) {
        return users.patch().uri("/api/users/me").headers(headers -> bearer(headers, token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of(
                        "email", form.getEmail(), "firstName", nullToEmpty(form.getFirstName()),
                        "lastName", nullToEmpty(form.getLastName()))).retrieve().body(UserView.class);
    }

    public void changePassword(String token, ChangePasswordForm form) {
        users.post().uri("/api/users/me/change-password").headers(headers -> bearer(headers, token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of(
                        "currentPassword", form.getCurrentPassword(), "newPassword", form.getNewPassword()))
                .retrieve().toBodilessEntity();
    }

    public PageResponse<UserView> adminUsers(String token, int page, int size, String search) {
        return users.get().uri(uri("/api/users/admin", page, size, Map.of("search", nullToEmpty(search))))
                .headers(headers -> bearer(headers, token)).retrieve().body(new ParameterizedTypeReference<>() { });
    }

    public UserView adminUser(String token, UUID id) {
        return users.get().uri("/api/users/admin/{id}", id).headers(headers -> bearer(headers, token))
                .retrieve().body(UserView.class);
    }

    public UserView setUserStatus(String token, UUID id, boolean enabled) {
        return users.patch().uri("/api/users/admin/{id}/status", id).headers(headers -> bearer(headers, token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("enabled", enabled)).retrieve().body(UserView.class);
    }

    public UserView setUserRoles(String token, UUID id, Set<String> roles) {
        Set<String> prefixed = roles.stream().map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return users.put().uri("/api/users/admin/{id}/roles", id).headers(headers -> bearer(headers, token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("roles", prefixed)).retrieve().body(UserView.class);
    }

    public PageResponse<ProductView> adminProducts(String token, int page, int size, String search) {
        String uri = UriComponentsBuilder.fromPath("/api/store/admin/products").queryParam("page", page)
                .queryParam("size", size).queryParam("q", nullToEmpty(search)).build().encode().toUriString();
        return store.get().uri(uri).headers(headers -> bearer(headers, token)).retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    public ProductView createProduct(String token, ProductForm form) {
        return store.post().uri("/api/store/admin/products").headers(headers -> bearer(headers, token))
                .contentType(MediaType.APPLICATION_JSON).body(productBody(form)).retrieve().body(ProductView.class);
    }

    public ProductView updateProduct(String token, UUID id, ProductForm form) {
        return store.put().uri("/api/store/admin/products/{id}", id).headers(headers -> bearer(headers, token))
                .contentType(MediaType.APPLICATION_JSON).body(productBody(form)).retrieve().body(ProductView.class);
    }

    public ProductView setProductActive(String token, UUID id, boolean active) {
        return store.patch().uri("/api/store/admin/products/{id}/activation", id).headers(headers -> bearer(headers, token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("active", active)).retrieve().body(ProductView.class);
    }

    public PageResponse<InventoryView> inventory(String token, int page, int size, String search) {
        return store.get().uri(uri("/api/store/admin/inventory", page, size, Map.of("q", nullToEmpty(search))))
                .headers(headers -> bearer(headers, token)).retrieve().body(new ParameterizedTypeReference<>() { });
    }

    public InventoryView updateInventory(String token, UUID productId, int quantity) {
        return store.put().uri("/api/store/admin/inventory/{id}", productId).headers(headers -> bearer(headers, token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("quantity", quantity)).retrieve().body(InventoryView.class);
    }

    public PageResponse<OrderView> adminOrders(String token, int page, int size, String status, String search) {
        Map<String, String> filters = new java.util.LinkedHashMap<>();
        filters.put("status", nullToEmpty(status));
        filters.put("search", nullToEmpty(search));
        return orders.get().uri(uri("/api/orders/admin", page, size, filters))
                .headers(headers -> bearer(headers, token)).retrieve().body(new ParameterizedTypeReference<>() { });
    }

    public ServiceStatusView health(String baseName, RestClient client) {
        try {
            Map<?, ?> result = client.get().uri("/actuator/health").retrieve().body(Map.class);
            boolean up = result != null && "UP".equals(result.get("status"));
            return new ServiceStatusView(baseName, up, up ? "Available" : "Degraded");
        } catch (Exception ignored) {
            return new ServiceStatusView(baseName, false, "Unavailable");
        }
    }

    public List<ServiceStatusView> serviceHealth() {
        return List.of(
                registryHealth(),
                new ServiceStatusView("Web UI + Redis session", true, "Available"),
                health("Authentication + Redis", auth),
                health("Users + PostgreSQL", users),
                health("Store + PostgreSQL/Kafka", store),
                health("Orders + PostgreSQL/Kafka", orders)
        );
    }

    private ServiceStatusView registryHealth() {
        try {
            EurekaClient client = eurekaClient.getIfAvailable();
            boolean available = client != null && client.getApplications() != null
                    && !client.getApplications().getRegisteredApplications().isEmpty();
            return new ServiceStatusView("Eureka registry", available,
                    available ? "Registry cache available" : "Registry unavailable");
        } catch (RuntimeException ignored) {
            return new ServiceStatusView("Eureka registry", false, "Registry unavailable");
        }
    }

    private static String uri(String path, int page, int size, Map<String, String> filters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path).queryParam("page", page).queryParam("size", size);
        filters.forEach((key, value) -> { if (value != null && !value.isBlank()) builder.queryParam(key, value); });
        return builder.build().encode().toUriString();
    }

    private static Map<String, Object> productBody(ProductForm form) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", form.getName()); body.put("sku", form.getSku()); body.put("description", form.getDescription());
        body.put("price", form.getPrice()); body.put("category", form.getCategory()); body.put("active", form.isActive());
        return body;
    }

    private static void bearer(HttpHeaders headers, String token) { headers.setBearerAuth(token); }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private record QuoteResponse(List<QuoteItemView> items) { }
}
