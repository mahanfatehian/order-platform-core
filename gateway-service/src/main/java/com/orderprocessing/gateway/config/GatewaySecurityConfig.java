package com.orderprocessing.gateway.config;

import com.orderprocessing.gateway.security.AccessTokenClaimsValidator;
import com.orderprocessing.gateway.security.GatewayErrorWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class GatewaySecurityConfig {

    private static final String ACCESS_BLACKLIST_PREFIX = "blacklist:access:";
    private static final String TOKEN_VERSION_PREFIX = "user:token-version:";

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            JwtSecurityProperties properties,
            ReactiveJwtDecoder jwtDecoder,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter,
            ServerAuthenticationEntryPoint authenticationEntryPoint,
            ServerAccessDeniedHandler accessDeniedHandler
    ) {
        String[] publicPaths = properties.getPublicPaths().toArray(new String[0]);

        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(publicPaths).permitAll()
                        .anyExchange().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                );

        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            JwtSecurityProperties properties,
            ReactiveStringRedisTemplate redisTemplate
    ) {
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
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

        NimbusReactiveJwtDecoder delegate = NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(macAlgorithm)
                .build();
        delegate.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new AccessTokenClaimsValidator()
        ));

        return token -> delegate.decode(token)
                .flatMap(jwt -> validateRevocation(jwt, redisTemplate))
                .onErrorMap(
                        exception -> !(exception instanceof JwtException),
                        exception -> new JwtException("Token revocation state could not be verified", exception)
                );
    }

    private Mono<Jwt> validateRevocation(Jwt jwt, ReactiveStringRedisTemplate redisTemplate) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        long tokenVersion = ((Number) jwt.getClaim("tokenVersion")).longValue();
        return redisTemplate.hasKey(ACCESS_BLACKLIST_PREFIX + jwt.getId())
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return Mono.error(new JwtException("Access token has been revoked"));
                    }
                    return redisTemplate.opsForValue().get(TOKEN_VERSION_PREFIX + userId)
                            .switchIfEmpty(Mono.error(new JwtException("Access token has been revoked")))
                            .flatMap(current -> {
                                try {
                                    return Long.parseLong(current) == tokenVersion
                                            ? Mono.just(jwt)
                                            : Mono.error(new JwtException("Access token has been revoked"));
                                } catch (NumberFormatException exception) {
                                    return Mono.error(new JwtException("Token revocation state is invalid", exception));
                                }
                            });
                });
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> Optional
                .ofNullable(jwt.getClaimAsStringList("roles"))
                .orElseGet(List::of)
                .stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .map(authority -> (org.springframework.security.core.GrantedAuthority) authority)
                .toList());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    @Bean
    public ServerAuthenticationEntryPoint authenticationEntryPoint(GatewayErrorWriter errorWriter) {
        return (exchange, exception) -> errorWriter.write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Missing, invalid, expired or revoked access token"
        );
    }

    @Bean
    public ServerAccessDeniedHandler accessDeniedHandler(GatewayErrorWriter errorWriter) {
        return (exchange, exception) -> errorWriter.write(
                exchange,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "You do not have permission to access this resource"
        );
    }
}
