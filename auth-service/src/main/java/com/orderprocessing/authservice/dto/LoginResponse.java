// LoginResponse.java
package com.orderprocessing.authservice.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter; import lombok.Setter;

@Setter @Getter
public class LoginResponse {
    @Schema(description = "Short-lived JWT Access Token")
    private String accessToken;

    @Schema(description = "Long-lived JWT Refresh Token")
    private String refreshToken;

    public LoginResponse() {}
    public LoginResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken; this.refreshToken = refreshToken;
    }
}