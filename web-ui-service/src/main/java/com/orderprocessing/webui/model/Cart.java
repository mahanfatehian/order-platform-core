package com.orderprocessing.webui.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Cart implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private final Map<UUID, Integer> quantities = new LinkedHashMap<>();

    public synchronized Map<UUID, Integer> getQuantities() { return Map.copyOf(quantities); }
    public synchronized void put(UUID productId, int quantity) { quantities.put(productId, quantity); }
    public synchronized void remove(UUID productId) { quantities.remove(productId); }
    public synchronized void clear() { quantities.clear(); }
    public synchronized void removeUnchanged(Map<UUID, Integer> orderedQuantities) {
        orderedQuantities.forEach((productId, orderedQuantity) ->
                quantities.computeIfPresent(productId,
                        (ignored, currentQuantity) -> currentQuantity.equals(orderedQuantity) ? null : currentQuantity));
    }
    public synchronized boolean isEmpty() { return quantities.isEmpty(); }
    public synchronized int distinctItems() { return quantities.size(); }
    public synchronized int totalItems() { return quantities.values().stream().mapToInt(Integer::intValue).sum(); }
}
