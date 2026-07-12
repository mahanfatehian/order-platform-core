package com.orderprocessing.storeservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@ConfigurationProperties(prefix = "app.security.store-internal")
@Validated
@Getter
@Setter
public class StoreInternalSecurityProperties {
    @NotBlank
    @Size(min = 32)
    private String apiKey;
}
