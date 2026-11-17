package com.orderprocessing.walletservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.security.web.RestAccessDeniedHandler;
import com.orderprocessing.security.web.RestAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(WalletInternalSecurityProperties.class)
public class WalletSecurityConfig {

    @Bean
    public WalletInternalApiKeyFilter walletInternalApiKeyFilter(WalletInternalSecurityProperties properties,
                                                                  ObjectMapper objectMapper) {
        return new WalletInternalApiKeyFilter(properties, objectMapper);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalWalletSecurityFilterChain(HttpSecurity http,
                                                                  WalletInternalApiKeyFilter filter) throws Exception {
        http
                .securityMatcher("/api/wallets/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain walletApiSecurityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        RestAuthenticationEntryPoint authenticationEntryPoint = new RestAuthenticationEntryPoint();
        RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler();
        http
                .securityMatcher("/api/wallets/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain walletFallbackSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus",
                                "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
