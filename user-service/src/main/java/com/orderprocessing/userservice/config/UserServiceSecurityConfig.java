package com.orderprocessing.userservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.security.config.JwtSecurityProperties;
import com.orderprocessing.security.web.RestAccessDeniedHandler;
import com.orderprocessing.security.web.RestAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.http.HttpMethod;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(InternalSecurityProperties.class)
public class UserServiceSecurityConfig {

    @Bean
    public InternalApiKeyFilter internalApiKeyFilter(
            InternalSecurityProperties properties,
            ObjectMapper objectMapper
    ) {
        return new InternalApiKeyFilter(properties, objectMapper);
    }

    @Bean
    @org.springframework.core.annotation.Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity http,
            InternalApiKeyFilter internalApiKeyFilter
    ) throws Exception {

        http
                .securityMatcher("/api/users/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler())
                )
                .addFilterBefore(internalApiKeyFilter, AbstractPreAuthenticatedProcessingFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    @org.springframework.core.annotation.Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityFilterChain applicationSecurityFilterChain(
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
                        .requestMatchers(HttpMethod.POST, "/api/users/register").permitAll()
                        .requestMatchers("/api/users/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );

        return http.build();
    }
}
