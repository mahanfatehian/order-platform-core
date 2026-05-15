package com.orderprocessing.security.config;

import com.orderprocessing.security.service.RedisTokenBlacklistService;
import com.orderprocessing.security.service.TokenBlacklistService;
import com.orderprocessing.security.web.RestAccessDeniedHandler;
import com.orderprocessing.security.web.RestAuthenticationEntryPoint;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.Filter;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@AutoConfiguration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenBlacklistService.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public TokenBlacklistService tokenBlacklistService(StringRedisTemplate stringRedisTemplate) {
        return new RedisTokenBlacklistService(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(
            JwtSecurityProperties properties,
            ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider
    ) {
        // Key derived exactly as in auth-service (HS384 for a 59-byte secret)
        SecretKey key = Keys.hmacShaKeyFor(
                properties.getSecret().getBytes(StandardCharsets.UTF_8)
        );

        // Map JCA algorithm name (e.g., "HmacSHA384") to Spring Security MacAlgorithm (e.g., HS384)
        MacAlgorithm macAlgorithm = MacAlgorithm.valueOf(key.getAlgorithm().replace("HmacSHA", "HS"));
        NimbusJwtDecoder delegate = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(macAlgorithm)
                .build();

        return token -> {
            Jwt jwt = delegate.decode(token);

            TokenBlacklistService tokenBlacklistService = tokenBlacklistServiceProvider.getIfAvailable();
            if (tokenBlacklistService == null) {
                return jwt;
            }

            String type = jwt.getClaimAsString("type");
            String jti = jwt.getId();

            if (jti != null) {
                if ("access".equals(type) && tokenBlacklistService.isAccessTokenBlacklisted(jti)) {
                    throw new JwtException("Access token has been revoked");
                }
                if ("refresh".equals(type) && tokenBlacklistService.isRefreshTokenBlacklisted(jti)) {
                    throw new JwtException("Refresh token has been revoked");
                }
            }
            return jwt;
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
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
            ObjectProvider<List<Filter>> securityFilters
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
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );

        List<Filter> filters = securityFilters.getIfAvailable();
        if (filters != null) {
            for (Filter filter : filters) {
                http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
            }
        }

        return http.build();
    }
}