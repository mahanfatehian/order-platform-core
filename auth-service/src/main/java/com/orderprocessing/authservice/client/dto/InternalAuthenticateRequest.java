package com.orderprocessing.authservice.client.dto;

import jakarta.validation.constraints.NotBlank;
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
public class InternalAuthenticateRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
