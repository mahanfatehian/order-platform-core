package com.orderprocessing.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalAuthenticatedUserResponse {
    private UUID id;
    private String username;
    private String email;
    private Set<String> roles;
}
