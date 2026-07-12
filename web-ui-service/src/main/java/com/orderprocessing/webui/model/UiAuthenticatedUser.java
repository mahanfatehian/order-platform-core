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
        return roles.stream().anyMatch(role -> "ADMIN".equals(role) || "ROLE_ADMIN".equals(role));
    }
}
