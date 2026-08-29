package com.orderprocessing.webui.service;

import com.orderprocessing.webui.config.WebUiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartServiceTest {
    private final WebUiProperties properties = new WebUiProperties();
    private final CartService service = new CartService(properties);

    @Test
    void storesOnlyProductIdAndQuantityInSessionCart() {
        MockHttpSession session = new MockHttpSession();
        UUID productId = UUID.randomUUID();
        service.put(session, productId, 3);
        assertThat(service.get(session).getQuantities()).containsEntry(productId, 3);
        assertThat(service.count(session)).isEqualTo(3);
    }

    @Test
    void rejectsQuantityOutsideConfiguredBoundary() {
        MockHttpSession session = new MockHttpSession();
        assertThatThrownBy(() -> service.put(session, UUID.randomUUID(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.put(session, UUID.randomUUID(), 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeAndClearAreIdempotent() {
        MockHttpSession session = new MockHttpSession();
        UUID id = UUID.randomUUID();
        service.put(session, id, 1);
        service.remove(session, id);
        service.remove(session, id);
        service.clear(session);
        assertThat(service.get(session).isEmpty()).isTrue();
    }

    @Test
    void successfulCheckoutCleanupPreservesConcurrentCartEdits() {
        MockHttpSession session = new MockHttpSession();
        UUID id = UUID.randomUUID();
        service.put(session, id, 2);
        Map<UUID, Integer> orderedSnapshot = service.checkoutSnapshot(session);

        service.put(session, id, 3);
        service.removeOrdered(session, orderedSnapshot);

        assertThat(service.get(session).getQuantities()).containsEntry(id, 3);
    }

    @Test
    void refusesANewProductOnceTheCartReachesTheQuoteContractLimit() {
        MockHttpSession session = new MockHttpSession();
        properties.getCart().setMaximumLineItems(3);
        for (int i = 0; i < 3; i++) {
            service.put(session, UUID.randomUUID(), 1);
        }

        assertThat(service.hasRoomFor(session, UUID.randomUUID())).isFalse();
        assertThatThrownBy(() -> service.put(session, UUID.randomUUID(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3");
        assertThat(service.get(session).distinctItems()).isEqualTo(3);
    }

    @Test
    void stillAcceptsAQuantityChangeForAProductAlreadyInAFullCart() {
        MockHttpSession session = new MockHttpSession();
        properties.getCart().setMaximumLineItems(2);
        UUID first = UUID.randomUUID();
        service.put(session, first, 1);
        service.put(session, UUID.randomUUID(), 1);

        // Updating an existing line does not grow the cart, so the limit must not block it.
        assertThat(service.hasRoomFor(session, first)).isTrue();
        service.put(session, first, 5);

        assertThat(service.get(session).getQuantities()).containsEntry(first, 5);
        assertThat(service.get(session).distinctItems()).isEqualTo(2);
    }

    @Test
    void defaultsToTheProductCountStoreServiceWillStillPrice() {
        // store-service rejects a quote carrying more than 100 products; a larger cart could never be priced,
        // and the cart page prices itself on every render.
        assertThat(service.maximumLineItems()).isEqualTo(100);
    }
}
