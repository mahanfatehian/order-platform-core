package com.orderprocessing.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(min = 3, max = 100)
    private String username;

    @Schema(description = "Unique email address", example = "john@example.com")
    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @Schema(description = "Raw password to be hashed before persistence", example = "StrongPass123")
    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @Schema(description = "First name", example = "John")
    @Size(max = 100)
    private String firstName;

    @Schema(description = "Last name", example = "Doe")
    @Size(max = 100)
    private String lastName;
}
