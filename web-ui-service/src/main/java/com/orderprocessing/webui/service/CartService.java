package com.orderprocessing.webui.service;

import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.model.Cart;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Map;

@Service
public class CartService {
    private static final String CART = CartService.class.getName() + ".CART";
    private final WebUiProperties properties;

    public CartService(WebUiProperties properties) { this.properties = properties; }

    public Cart get(HttpSession session) {
        Cart cart = (Cart) session.getAttribute(CART);
        if (cart == null) {
            cart = new Cart();
            session.setAttribute(CART, cart);
        }
        return cart;
    }

    public void put(HttpSession session, UUID productId, int quantity) {
        if (quantity < 1 || quantity > properties.getCart().getMaximumQuantity()) {
            throw new IllegalArgumentException("Quantity must be between 1 and " + properties.getCart().getMaximumQuantity());
        }
        get(session).put(productId, quantity);
        session.setAttribute(CART, get(session));
    }

    public void remove(HttpSession session, UUID productId) { get(session).remove(productId); session.setAttribute(CART, get(session)); }
    public void clear(HttpSession session) { get(session).clear(); session.setAttribute(CART, get(session)); }
    public Map<UUID, Integer> checkoutSnapshot(HttpSession session) {
        Map<UUID, Integer> snapshot = get(session).getQuantities();
        if (snapshot.isEmpty()) return Map.of();
        if (snapshot.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null
                || entry.getValue() < 1 || entry.getValue() > properties.getCart().getMaximumQuantity())) {
            throw new IllegalStateException("Cart contains an invalid quantity");
        }
        return snapshot;
    }
    public void removeOrdered(HttpSession session, Map<UUID, Integer> orderedQuantities) {
        Cart cart = get(session);
        cart.removeUnchanged(orderedQuantities);
        session.setAttribute(CART, cart);
    }
    public int count(HttpSession session) { return get(session).totalItems(); }
}
