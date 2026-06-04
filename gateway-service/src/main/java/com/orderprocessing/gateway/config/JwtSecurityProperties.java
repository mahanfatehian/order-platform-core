package com.orderprocessing.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtSecurityProperties {
    private List<String> publicPaths = new ArrayList<>();
    private String secret = "mySuperUltraSecureSecretKeyThatMustBeLongEnough123456";
}