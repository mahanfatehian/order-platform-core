package com.orderprocessing.security.config;

import com.orderprocessing.security.service.RedisTokenBlacklistService;
import com.orderprocessing.security.service.TokenBlacklistService;
import com.orderprocessing.security.service.TokenRevocationService;
import com.orderprocessing.security.web.CorrelationIdFilter;
import com.orderprocessing.security.web.RestAccessDeniedHandler;
import com.orderprocessing.security.web.RestAuthenticationEntryPoint;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableMethodSecurity
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class SecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityAutoConfiguration.class);

    // Provide a shared StringRedisTemplate bean
    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(TokenBlacklistService.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public TokenRevocationService tokenBlacklistService(StringRedisTemplate redisTemplate) {
        log.info("TokenRevocationService initialised with Redis");
        return new RedisTokenBlacklistService(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(CorrelationIdFilter.class)
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(
            JwtSecurityProperties properties,
            TokenRevocationService tokenRevocationService
    ) {
        SecretKey key = Keys.hmacShaKeyFor(
                properties.getSecret().getBytes(StandardCharsets.UTF_8)
        );

        MacAlgorithm macAlgorithm = MacAlgorithm.valueOf(key.getAlgorithm().replace("HmacSHA", "HS"));
        NimbusJwtDecoder delegate = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(macAlgorithm)
                .build();
        delegate.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new AccessTokenClaimsValidator()
        ));

        return token -> {
            Jwt jwt = delegate.decode(token);
            try {
                UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
                long tokenVersion = ((Number) jwt.getClaim("tokenVersion")).longValue();
                if (tokenRevocationService.isAccessTokenBlacklisted(jwt.getId())) {
                    throw new JwtException("Access token has been revoked");
                }
                OptionalLong currentVersion = tokenRevocationService.getTokenVersion(userId);
                if (currentVersion.isEmpty() || currentVersion.getAsLong() != tokenVersion) {
                    throw new JwtException("Access token has been revoked");
                }
                return jwt;
            } catch (JwtException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new JwtException("Token revocation state could not be verified", exception);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "jwtAuthenticationConverter")
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtAuthoritiesConverter());
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtSecurityProperties properties,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter
    ) throws Exception {

        String[] publicPaths = properties.getPublicPaths().toArray(new String[0]);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );

        return http.build();
    }
}
