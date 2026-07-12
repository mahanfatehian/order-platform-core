package com.orderprocessing.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Internal request payload for authenticating a user (consumed by Auth Service)")
public class InternalAuthenticateRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 50)
    @Schema(description = "The username of the user", example = "johndoe")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(max = 100)
    @Schema(description = "The raw password of the user", example = "password123")
    private String password;
}
