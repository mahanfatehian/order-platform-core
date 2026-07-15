package com.orderprocessing.webui.client;

import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.dto.*;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.exception.SessionExpiredException;
import com.orderprocessing.webui.form.*;
import com.orderprocessing.webui.model.UiSessionTokens;
import com.orderprocessing.webui.service.SessionTokenService;
import com.orderprocessing.webui.service.UiAuthenticationService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Component
public class AuthenticatedPlatformClient {
    private final PlatformClient client;
    private final SessionTokenService tokens;
    private final UiAuthenticationService authentication;
    private final WebUiProperties properties;

    public AuthenticatedPlatformClient(PlatformClient client, SessionTokenService tokens,
                                       UiAuthenticationService authentication, WebUiProperties properties) {
        this.client = client;
        this.tokens = tokens;
        this.authentication = authentication;
        this.properties = properties;
    }

    public PageResponse<ProductView> products(int page, int size, String q, String sort, boolean inStock) {
        return get(token -> client.products(token, page, size, q, sort, inStock));
    }
    public ProductView product(UUID id) { return get(token -> client.product(token, id)); }
    public CartView quote(Map<UUID, Integer> quantities) { return get(token -> client.quote(token, quantities)); }
    public OrderView createOrder(Map<UUID, Integer> quantities, String key) {
        return mutate(token -> client.createOrder(token, quantities, key));
    }
    public PageResponse<OrderView> myOrders(int page, int size) { return get(token -> client.myOrders(token, page, size)); }
    public OrderView order(UUID id) { return get(token -> client.order(token, id)); }
    public OrderView cancelOrder(UUID id) { return mutate(token -> client.cancelOrder(token, id)); }
    public UserView profile() { return get(client::profile); }
    public UserView updateProfile(ProfileForm form) { return mutate(token -> client.updateProfile(token, form)); }
    public Void changePassword(ChangePasswordForm form) { return mutate(token -> { client.changePassword(token, form); return null; }); }
    public PageResponse<UserView> adminUsers(int page, int size, String search) { return get(t -> client.adminUsers(t, page, size, search)); }
    public UserView adminUser(UUID id) { return get(t -> client.adminUser(t, id)); }
    public UserView setUserStatus(UUID id, boolean enabled) { return mutate(t -> client.setUserStatus(t, id, enabled)); }
    public UserView setUserRoles(UUID id, Set<String> roles) { return mutate(t -> client.setUserRoles(t, id, roles)); }
    public PageResponse<ProductView> adminProducts(int page, int size, String search) { return get(t -> client.adminProducts(t, page, size, search)); }
    public ProductView createProduct(ProductForm form) { return mutate(t -> client.createProduct(t, form)); }
    public ProductView updateProduct(UUID id, ProductForm form) { return mutate(t -> client.updateProduct(t, id, form)); }
    public ProductView setProductActive(UUID id, boolean active) { return mutate(t -> client.setProductActive(t, id, active)); }
    public PageResponse<InventoryView> inventory(int page, int size, String search) { return get(t -> client.inventory(t, page, size, search)); }
    public InventoryView updateInventory(UUID id, int quantity) { return mutate(t -> client.updateInventory(t, id, quantity)); }
    public PageResponse<OrderView> adminOrders(int page, int size, String status, String search) {
        return get(t -> client.adminOrders(t, page, size, status, search));
    }
    public PageResponse<OrderView> fulfillmentOrders(int page, int size, String status) {
        return get(t -> client.fulfillmentOrders(t, page, size, status));
    }
    public OrderView packOrder(UUID id) { return mutate(t -> client.packOrder(t, id)); }
    public OrderView shipOrder(UUID id, String trackingReference) {
        return mutate(t -> client.shipOrder(t, id, trackingReference));
    }
    public OrderView deliverOrder(UUID id) { return mutate(t -> client.deliverOrder(t, id)); }
    public List<OrderHistoryView> orderHistory(UUID id) { return get(t -> client.orderHistory(t, id)); }
    public List<ServiceStatusView> serviceHealth() { return client.serviceHealth(); }

    private <T> T get(Function<String, T> operation) {
        UiSessionTokens tokenPair = validTokens();
        try {
            return operation.apply(tokenPair.accessToken());
        } catch (BackendClientException exception) {
            if (exception.getStatus().value() != 401) throw exception;
            UiSessionTokens refreshed = forceRefresh();
            try {
                return operation.apply(refreshed.accessToken());
            } catch (BackendClientException retryFailure) {
                if (retryFailure.getStatus().value() == 401) {
                    authentication.expireCurrentSession();
                    throw new SessionExpiredException("Your session expired. Please sign in again.", retryFailure);
                }
                throw retryFailure;
            }
        }
    }

    private <T> T mutate(Function<String, T> operation) {
        try {
            return operation.apply(validTokens().accessToken());
        } catch (BackendClientException exception) {
            if (exception.getStatus().value() == 401) {
                authentication.expireCurrentSession();
                throw new SessionExpiredException("Your session expired. Please sign in again.", exception);
            }
            throw exception;
        }
    }

    private UiSessionTokens validTokens() {
        UiSessionTokens current = tokens.current().orElseThrow(() -> new SessionExpiredException("Your session expired", null));
        Instant threshold = Instant.now().plus(properties.getSecurity().getRefreshSkew());
        return current.accessExpiresWithin(threshold) ? authentication.refreshCurrentSession() : current;
    }

    private UiSessionTokens forceRefresh() {
        UiSessionTokens current = tokens.current().orElseThrow(() -> new SessionExpiredException("Your session expired", null));
        // force the normal refresh path by replacing only the in-session access expiry
        tokens.saveCurrent(new UiSessionTokens(current.accessToken(), current.refreshToken(), Instant.EPOCH, current.refreshExpiresAt()));
        return authentication.refreshCurrentSession();
    }
}
