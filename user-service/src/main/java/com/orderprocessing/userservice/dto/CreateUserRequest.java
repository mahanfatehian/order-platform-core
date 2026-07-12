package com.orderprocessing.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for creating a user")
public class CreateUserRequest {

    @Schema(description = "Unique username", example = "john_doe")
    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username may contain only letters, numbers, dot, underscore and hyphen")
    private String username;

    @Schema(description = "Unique email address", example = "john@example.com")
    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @Schema(description = "Raw password to be hashed before persistence", example = "StrongPass123")
    @NotBlank
    @Size(min = 8, max = 100)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "Password must contain uppercase, lowercase, number and special characters"
    )
    private String password;

    @Schema(description = "First name", example = "John")
    @Size(max = 100)
    private String firstName;

    @Schema(description = "Last name", example = "Doe")
    @Size(max = 100)
    private String lastName;
}
