package com.orderprocessing.userservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.internal")
@Getter
@Setter
public class InternalSecurityProperties {

    private String apiKey;
}
