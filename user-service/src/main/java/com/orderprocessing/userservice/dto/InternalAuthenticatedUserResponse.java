package com.orderprocessing.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Internal response payload containing authenticated user details and roles")
public class InternalAuthenticatedUserResponse {

    @Schema(description = "Unique identifier of the user", example = "c1111111-1111-1111-1111-111111111111")
    private UUID id;

    @Schema(description = "Username of the authenticated user", example = "johndoe")
    private String username;

    @Schema(description = "Email address of the user", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Set of roles assigned to the user", example = "[\"ROLE_USER\"]")
    private Set<String> roles;
}