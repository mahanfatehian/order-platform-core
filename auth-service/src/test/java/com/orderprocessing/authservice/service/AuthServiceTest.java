package com.orderprocessing.authservice.service;

import com.orderprocessing.authservice.client.UserServiceClient;
import com.orderprocessing.authservice.client.dto.InternalAuthenticatedUserResponse;
import com.orderprocessing.authservice.client.dto.InternalUserStateResponse;
import com.orderprocessing.authservice.dto.LoginRequest;
import com.orderprocessing.authservice.dto.LoginResponse;
import com.orderprocessing.authservice.exception.AuthenticationFailedException;
import com.orderprocessing.authservice.security.JwtTokenService;
import com.orderprocessing.security.config.JwtSecurityProperties;
import com.orderprocessing.security.service.TokenRevocationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void refreshLoadsCurrentRolesAndRotatesOnce() {
        UUID userId = UUID.randomUUID();
        JwtTokenService tokens = new JwtTokenService(properties());
        JwtTokenService.TokenPair original = tokens.generateTokenPair(
                "customer", Set.of("ROLE_USER"), userId, 77L);

        UserServiceClient users = mock(UserServiceClient.class);
        InternalUserStateResponse state = new InternalUserStateResponse();
        state.setId(userId);
        state.setUsername("customer");
        state.setEnabled(true);
        state.setAccountNonLocked(true);
        state.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
        when(users.getCurrentState(userId)).thenReturn(state);

        TokenRevocationService revocation = mock(TokenRevocationService.class);
        when(revocation.getTokenVersion(userId)).thenReturn(OptionalLong.of(77L));
        when(revocation.isRefreshTokenBlacklisted(any())).thenReturn(false);
        when(revocation.rotateRefreshToken(any(), any(), any(), any(), any(), anyLong())).thenReturn(true);

        LoginResponse refreshed = new AuthService(users, tokens, revocation).refresh(original.refreshToken());
        Claims accessClaims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(refreshed.getAccessToken())
                .getPayload();

        assertThat(accessClaims.get("roles", List.class))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void disabledUserCannotLogin() {
        UserServiceClient users = mock(UserServiceClient.class);
        InternalAuthenticatedUserResponse disabled = InternalAuthenticatedUserResponse.builder()
                .id(UUID.randomUUID())
                .username("disabled")
                .roles(Set.of("ROLE_USER"))
                .enabled(false)
                .accountNonLocked(true)
                .build();
        when(users.authenticate(any())).thenReturn(disabled);
        AuthService service = new AuthService(
                users,
                new JwtTokenService(properties()),
                mock(TokenRevocationService.class)
        );
        LoginRequest request = new LoginRequest();
        request.setUsername("disabled");
        request.setPassword("Password1!");

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    private JwtSecurityProperties properties() {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setSecret(SECRET);
        properties.setExpiration(60_000);
        properties.setRefreshExpiration(120_000);
        return properties;
    }
}
