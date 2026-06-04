package com.orderprocessing.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, JwtSecurityProperties properties) {
        String[] publicPaths = properties.getPublicPaths().toArray(new String[0]);

        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(publicPaths).permitAll() // Allow login, swagger, etc. without JWT
                        .anyExchange().authenticated()         // Require JWT for everything else
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder(properties)))
                );

        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(JwtSecurityProperties properties) {
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);

        // Mirror the exact logic of jjwt's Keys.hmacShaKeyFor() to determine the correct algorithm
        // based on the secret key length. This ensures the Gateway uses the same algorithm (HS256, HS384, or HS512)
        SecretKey secretKey;
        MacAlgorithm macAlgorithm;

        if (keyBytes.length >= 64) {
            secretKey = new SecretKeySpec(keyBytes, "HmacSHA512");
            macAlgorithm = MacAlgorithm.HS512;
        } else if (keyBytes.length >= 48) {
            secretKey = new SecretKeySpec(keyBytes, "HmacSHA384");
            macAlgorithm = MacAlgorithm.HS384;
        } else {
            secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
            macAlgorithm = MacAlgorithm.HS256;
        }

        return NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(macAlgorithm)
                .build();
    }
}