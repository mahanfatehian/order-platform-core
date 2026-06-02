package com.orderprocessing.storeservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.store-internal")
@Getter
@Setter
public class StoreInternalSecurityProperties {
    private String apiKey;
}