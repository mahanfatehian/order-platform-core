// LoginRequest.java
package com.orderprocessing.authservice.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter; import lombok.Setter;

@Getter @Setter
public class LoginRequest {
    @Schema(description = "User's username", example = "johndoe")
    @NotBlank @Size(max = 50) private String username;

    @Schema(description = "User's raw password", example = "johndoe")
    @NotBlank @Size(max = 100) private String password;
}
