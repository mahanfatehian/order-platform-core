package com.orderprocessing.authservice.service;

import com.orderprocessing.authservice.client.UserServiceClient;
import com.orderprocessing.authservice.client.dto.InternalAuthenticateRequest;
import com.orderprocessing.authservice.client.dto.InternalAuthenticatedUserResponse;
import com.orderprocessing.authservice.dto.LoginRequest;
import com.orderprocessing.authservice.dto.LoginResponse;
import com.orderprocessing.authservice.security.JwtTokenService;
import com.orderprocessing.security.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class AuthService {
    private final UserServiceClient userServiceClient;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthService(UserServiceClient userServiceClient, AuthenticationManager authenticationManager,
                       JwtTokenService jwtTokenService,
                       TokenBlacklistService tokenBlacklistService) {
        this.userServiceClient = userServiceClient;
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public LoginResponse login(LoginRequest request) {

        InternalAuthenticatedUserResponse user =
                userServiceClient.authenticate(
                        new InternalAuthenticateRequest(
                                request.getUsername(),
                                request.getPassword()
                        )
                );

        Set<String> roles = user.getRoles();

        String accessToken =
                jwtTokenService.generateAccessToken(user.getUsername(), roles);

        String refreshToken =
                jwtTokenService.generateRefreshToken(user.getUsername());

        return new LoginResponse(accessToken, refreshToken);
    }

    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new RuntimeException("Refresh token is required");
        }

        if (!jwtTokenService.isRefreshToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token type");
        }

        String refreshJti = jwtTokenService.extractJti(refreshToken);
        if (tokenBlacklistService.isRefreshTokenBlacklisted(refreshJti)) {
            throw new RuntimeException("Refresh token has been revoked");
        }

        String username = jwtTokenService.extractUsername(refreshToken);

        Claims claims = jwtTokenService.parse(refreshToken);
        Instant refreshExpiresAt = claims.getExpiration().toInstant();

        tokenBlacklistService.blacklistRefreshToken(refreshJti, refreshExpiresAt);

        String newAccessToken = jwtTokenService.generateAccessToken(username, Collections.emptySet());
        String newRefreshToken = jwtTokenService.generateRefreshToken(username);

        return new LoginResponse(newAccessToken, newRefreshToken);
    }

    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }

        String accessToken = authorizationHeader.substring(7);

        if (!jwtTokenService.isAccessToken(accessToken)) {
            throw new RuntimeException("Invalid access token type");
        }

        String accessJti = jwtTokenService.extractJti(accessToken);
        Instant accessExpiresAt = jwtTokenService.extractExpiration(accessToken);

        tokenBlacklistService.blacklistAccessToken(accessJti, accessExpiresAt);
    }
}
