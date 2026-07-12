package com.orderprocessing.webui.service;

import com.orderprocessing.webui.config.WebUiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;

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
}
