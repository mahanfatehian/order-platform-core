package com.orderprocessing.webui.config;

import com.orderprocessing.webui.service.UiAuthenticationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http, UiAuthenticationService authenticationService)
            throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/assets/**", "/webjars/**", "/error/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/admin/warehouse", "/admin/warehouse/**").hasRole("WAREHOUSE")
                        .requestMatchers("/admin/delivery", "/admin/delivery/**").hasRole("DELIVERY")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/app/profile", "/app/profile/**").authenticated()
                        .requestMatchers("/app/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/fragments/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").disable())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler((request, response, authentication) -> authenticationService.logoutCurrentSession())
                        .logoutSuccessHandler((request, response, authentication) -> {
                            ResponseCookie expiredSession = ResponseCookie.from("ORDER_PLATFORM_SESSION", "")
                                    .path("/")
                                    .httpOnly(true)
                                    .secure(request.isSecure())
                                    .sameSite("Lax")
                                    .maxAge(Duration.ZERO)
                                    .build();
                            response.addHeader(HttpHeaders.SET_COOKIE, expiredSession.toString());
                            response.sendRedirect(request.getContextPath() + "/login?logout");
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("ORDER_PLATFORM_SESSION"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
                                new HttpStatusEntryPoint(UNAUTHORIZED).commence(request, response, authException);
                            } else {
                                response.sendRedirect(request.getContextPath() + "/login");
                            }
                        })
                        .accessDeniedPage("/error/403"));
        return http.build();
    }

    @Bean
    JwtDecoder uiJwtDecoder(WebUiProperties properties) {
        byte[] bytes = properties.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8);
        MacAlgorithm algorithm = bytes.length >= 64 ? MacAlgorithm.HS512
                : bytes.length >= 48 ? MacAlgorithm.HS384 : MacAlgorithm.HS256;
        String keyAlgorithm = bytes.length >= 64 ? "HmacSHA512" : bytes.length >= 48 ? "HmacSHA384" : "HmacSHA256";
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes, keyAlgorithm)).macAlgorithm(algorithm).build();
    }
}
