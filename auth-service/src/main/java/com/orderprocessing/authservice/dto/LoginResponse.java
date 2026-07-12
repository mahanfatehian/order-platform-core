// LoginResponse.java
package com.orderprocessing.authservice.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter; import lombok.Setter;

import java.time.Instant;

@Setter @Getter
public class LoginResponse {
    @Schema(description = "Short-lived JWT Access Token")
    private String accessToken;

    @Schema(description = "Long-lived JWT Refresh Token")
    private String refreshToken;

    @Schema(description = "Access token expiration instant")
    private Instant accessTokenExpiresAt;

    @Schema(description = "Refresh token expiration instant")
    private Instant refreshTokenExpiresAt;

    public LoginResponse() {}
    public LoginResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken; this.refreshToken = refreshToken;
    }

    public LoginResponse(
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt
    ) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
    }
}
