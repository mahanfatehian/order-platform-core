package com.orderprocessing.walletservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.security.web.RestAccessDeniedHandler;
import com.orderprocessing.security.web.RestAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
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
