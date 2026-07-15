package com.orderprocessing.webui.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

public record UiAuthenticatedUser(UUID id, String username, Set<String> roles) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public UiAuthenticatedUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isWarehouse() { return hasRole("WAREHOUSE"); }
    public boolean isDelivery() { return hasRole("DELIVERY"); }
    public boolean isCustomer() { return hasRole("USER"); }

    public boolean hasRole(String role) {
        String normalized = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return roles.stream().anyMatch(candidate -> normalized.equals(
                candidate.startsWith("ROLE_") ? candidate : "ROLE_" + candidate));
    }
}
