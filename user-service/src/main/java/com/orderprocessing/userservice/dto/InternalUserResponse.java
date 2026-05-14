package com.orderprocessing.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalUserResponse {
    private UUID id;
    private String username;
    private String email;
    private String passwordHash;
    private List<String> roles;
    private boolean enabled;
}
