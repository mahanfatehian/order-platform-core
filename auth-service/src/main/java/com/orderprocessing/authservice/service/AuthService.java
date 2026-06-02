package com.orderprocessing.authservice.service;

import com.orderprocessing.authservice.client.UserServiceClient;
import com.orderprocessing.authservice.client.dto.InternalAuthenticateRequest;
import com.orderprocessing.authservice.client.dto.InternalAuthenticatedUserResponse;
import com.orderprocessing.authservice.dto.LoginRequest;
import com.orderprocessing.authservice.dto.LoginResponse;
import com.orderprocessing.authservice.security.JwtTokenService;
import com.orderprocessing.security.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserServiceClient userServiceClient;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthService(UserServiceClient userServiceClient,
                       AuthenticationManager authenticationManager,
                       JwtTokenService jwtTokenService,
                       TokenBlacklistService tokenBlacklistService) {
        this.userServiceClient = userServiceClient;
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public LoginResponse login(LoginRequest request) {
        InternalAuthenticatedUserResponse user =
                userServiceClient.authenticate(
                        new InternalAuthenticateRequest(request.getUsername(), request.getPassword())
                );

        Set<String> roles = user.getRoles();

        String accessToken = jwtTokenService.generateAccessToken(user.getUsername(), roles, user.getId());

        String accessJti = jwtTokenService.extractJti(accessToken);
        Instant accessExpiresAt = jwtTokenService.extractExpiration(accessToken);

        String refreshToken = jwtTokenService.generateRefreshToken(user.getUsername(), accessJti, accessExpiresAt, user.getId());

        return new LoginResponse(accessToken, refreshToken);
    }

    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new RuntimeException("Refresh token is required");
        }
        if (!jwtTokenService.isRefreshToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token type");
        }

        // Check version
        if (!jwtTokenService.isTokenVersionValid(refreshToken)) {
            log.warn("Refresh token rejected - version mismatch");
            throw new RuntimeException("Refresh token has been revoked (version mismatch)");
        }

        String refreshJti = jwtTokenService.extractJti(refreshToken);
        if (tokenBlacklistService.isRefreshTokenBlacklisted(refreshJti)) {
            throw new RuntimeException("Refresh token has been revoked");
        }

        // Blacklist the old access token associated with this refresh token
        String oldAccessJti = jwtTokenService.extractAccessJti(refreshToken);
        Instant oldAccessExp = jwtTokenService.extractAccessExpiration(refreshToken);
        if (oldAccessJti != null && oldAccessExp != null) {
            log.info("Blacklisting old access token jti={} (associated with refresh token)", oldAccessJti);
            tokenBlacklistService.blacklistAccessToken(oldAccessJti, oldAccessExp);
        }

        // Blacklist the refresh token itself
        String username = jwtTokenService.extractUsername(refreshToken);
        Claims claims = jwtTokenService.parse(refreshToken);
        Instant refreshExpiresAt = claims.getExpiration().toInstant();
        tokenBlacklistService.blacklistRefreshToken(refreshJti, refreshExpiresAt);

        // Issue new tokens
        String newAccessToken = jwtTokenService.generateAccessToken(username, Collections.emptySet(), extractUserIdFromRefreshToken(refreshToken));
        String newAccessJti = jwtTokenService.extractJti(newAccessToken);
        Instant newAccessExp = jwtTokenService.extractExpiration(newAccessToken);

        String newRefreshToken = jwtTokenService.generateRefreshToken(username, newAccessJti, newAccessExp, extractUserIdFromRefreshToken(refreshToken));

        return new LoginResponse(newAccessToken, newRefreshToken);
    }

    private UUID extractUserIdFromRefreshToken(String refreshToken) {
        return jwtTokenService.extractUserId(refreshToken);
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
        String username = jwtTokenService.extractUsername(accessToken);

        log.info("Logging out user {} - access token jti={}", username, accessJti);

        // Blacklist the specific access token
        tokenBlacklistService.blacklistAccessToken(accessJti, accessExpiresAt);

        // Invalidate ALL tokens for this user by incrementing the token version
        jwtTokenService.incrementTokenVersion(username);
        log.info("Token version incremented for user {}", username);
    }
}