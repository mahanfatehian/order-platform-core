package com.orderprocessing.webui.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserView(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean accountNonLocked,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public UserView { roles = roles == null ? Set.of() : Set.copyOf(roles); }
    public String displayName() {
        String value = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return value.isBlank() ? username : value;
    }
}
