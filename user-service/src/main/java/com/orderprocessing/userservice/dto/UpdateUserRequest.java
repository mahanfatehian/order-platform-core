package com.orderprocessing.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for updating a user")
public class UpdateUserRequest {

    @Schema(description = "Updated username", example = "john_doe_updated")
    @Size(min = 3, max = 100)
    private String username;

    @Schema(description = "Updated email address", example = "john.updated@example.com")
    @Email
    @Size(max = 150)
    private String email;

    @Schema(description = "Updated password", example = "NewStrongPass123")
    @Size(min = 6, max = 100)
    private String password;

    @Schema(description = "Updated first name", example = "John")
    @Size(max = 100)
    private String firstName;

    @Schema(description = "Updated last name", example = "Doe")
    @Size(max = 100)
    private String lastName;

    @Schema(description = "Whether the user is enabled", example = "true")
    private Boolean enabled;
}
