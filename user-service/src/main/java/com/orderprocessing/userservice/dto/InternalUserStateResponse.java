package com.orderprocessing.userservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@Builder
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
