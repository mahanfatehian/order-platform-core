package com.orderprocessing.authservice.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class InternalUserStateResponse {
    private UUID id;
    private String username;
    private String email;
    private boolean enabled;
    private boolean accountNonLocked;
    private Set<String> roles;

    public boolean isActive() {
        return enabled && accountNonLocked;
    }
}
