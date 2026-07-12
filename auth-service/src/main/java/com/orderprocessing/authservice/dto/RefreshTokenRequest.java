package com.orderprocessing.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Schema(description = "Request payload for refreshing an expired access token")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    @Size(max = 4096, message = "Refresh token is too long")
    @Schema(description = "The long-lived JWT refresh token", example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huZG9lIn0...")
    private String refreshToken;

    public RefreshTokenRequest() {
    }

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
