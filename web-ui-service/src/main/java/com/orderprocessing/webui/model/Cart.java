package com.orderprocessing.webui.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Cart implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private final Map<UUID, Integer> quantities = new LinkedHashMap<>();

    public Map<UUID, Integer> getQuantities() { return Map.copyOf(quantities); }
    public void put(UUID productId, int quantity) { quantities.put(productId, quantity); }
    public void remove(UUID productId) { quantities.remove(productId); }
    public void clear() { quantities.clear(); }
    public boolean isEmpty() { return quantities.isEmpty(); }
    public int distinctItems() { return quantities.size(); }
    public int totalItems() { return quantities.values().stream().mapToInt(Integer::intValue).sum(); }
}
