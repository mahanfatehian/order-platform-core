package com.orderprocessing.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "User response payload")
public class UserResponse {

    @Schema(description = "User unique identifier")
    private UUID id;

    @Schema(description = "Username", example = "john_doe")
    private String username;

    @Schema(description = "Email address", example = "john@example.com")
    private String email;

    @Schema(description = "First name", example = "John")
    private String firstName;

    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Schema(description = "Whether the user is enabled", example = "true")
    private boolean enabled;

    @Schema(description = "Whether the account is not locked", example = "true")
    private boolean accountNonLocked;

    @Schema(description = "Creation timestamp")
    private OffsetDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private OffsetDateTime updatedAt;

    @Schema(description = "Assigned roles")
    private Set<String> roles;
}
