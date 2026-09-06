package com.orderprocessing.authservice.service;

import com.orderprocessing.authservice.client.UserServiceClient;
import com.orderprocessing.authservice.client.dto.InternalAuthenticateRequest;
import com.orderprocessing.authservice.client.dto.InternalAuthenticatedUserResponse;
import com.orderprocessing.authservice.client.dto.InternalUserStateResponse;
import com.orderprocessing.authservice.dto.LoginRequest;
import com.orderprocessing.authservice.dto.LoginResponse;
import com.orderprocessing.authservice.exception.AuthenticationFailedException;
import com.orderprocessing.authservice.exception.ServiceUnavailableException;
import com.orderprocessing.authservice.security.JwtTokenService;
import com.orderprocessing.security.service.TokenRevocationService;
import feign.FeignException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final UserServiceClient userServiceClient;
    private final JwtTokenService jwtTokenService;
    private final TokenRevocationService tokenRevocationService;

    public AuthService(
            UserServiceClient userServiceClient,
            JwtTokenService jwtTokenService,
            TokenRevocationService tokenRevocationService
    ) {
        this.userServiceClient = userServiceClient;
        this.jwtTokenService = jwtTokenService;
        this.tokenRevocationService = tokenRevocationService;
    }

    public LoginResponse login(LoginRequest request) {
        InternalAuthenticatedUserResponse user;
        try {
            user = userServiceClient.authenticate(
                    new InternalAuthenticateRequest(request.getUsername(), request.getPassword()));
        } catch (FeignException.Unauthorized | FeignException.NotFound exception) {
            throw new AuthenticationFailedException("Invalid username or password", exception);
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("User service is temporarily unavailable", exception);
        }
        if (user == null || user.getId() == null || !user.isEnabled() || !user.isAccountNonLocked()) {
            throw new AuthenticationFailedException("Invalid username or password");
        }
        long tokenVersion = tokenRevocationService.getOrCreateTokenVersion(user.getId());
        return response(jwtTokenService.generateTokenPair(
                user.getUsername(), safeRoles(user.getRoles()), user.getId(), tokenVersion));
    }

    public LoginResponse refresh(String refreshToken) {
        JwtTokenService.RefreshTokenClaims claims;
        try {
            claims = jwtTokenService.parseRefreshToken(refreshToken);
        } catch (io.jsonwebtoken.JwtException exception) {
            throw new AuthenticationFailedException("Invalid or expired refresh token", exception);
        }

        OptionalLong currentVersion = tokenRevocationService.getTokenVersion(claims.userId());
        if (currentVersion.isEmpty() || currentVersion.getAsLong() != claims.tokenVersion()) {
            throw new AuthenticationFailedException("Invalid or revoked refresh token");
        }
        if (tokenRevocationService.isRefreshTokenBlacklisted(claims.jti())) {
            // The version still matches, so this family was never signed out, yet this token has already been
            // spent by a rotation. Someone is replaying a consumed refresh token, and the holder of whatever it
            // rotated into is indistinguishable from the legitimate user. Rejecting only this request would leave
            // that successor working, so the whole family goes: incrementing the version invalidates every access
            // and refresh token issued under it, forcing a real sign-in.
            tokenRevocationService.incrementTokenVersion(claims.userId());
            throw new AuthenticationFailedException("Invalid or revoked refresh token");
        }

        InternalUserStateResponse user;
        try {
            user = userServiceClient.getCurrentState(claims.userId());
        } catch (FeignException.NotFound exception) {
            throw new AuthenticationFailedException("Invalid or revoked refresh token", exception);
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("User service is temporarily unavailable", exception);
        }
        if (user == null
                || !claims.userId().equals(user.getId())
                || !user.isActive()) {
            throw new AuthenticationFailedException("Invalid or revoked refresh token");
        }

        JwtTokenService.TokenPair nextPair = jwtTokenService.generateTokenPair(
                user.getUsername(), safeRoles(user.getRoles()), user.getId(), claims.tokenVersion());
        boolean rotated = tokenRevocationService.rotateRefreshToken(
                claims.jti(),
                claims.expiresAt(),
                claims.linkedAccessJti(),
                claims.linkedAccessExpiresAt(),
                claims.userId(),
                claims.tokenVersion()
        );
        if (!rotated) {
            // Rotation is the single-winner step. Losing it after the checks above passed means the token was
            // consumed between them, which is the same replay signature seen from a narrower window.
            tokenRevocationService.incrementTokenVersion(claims.userId());
            throw new AuthenticationFailedException("Invalid, stale or already used refresh token");
        }
        return response(nextPair);
    }

    public void logout(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        long tokenVersion = ((Number) jwt.getClaim("tokenVersion")).longValue();
        tokenRevocationService.revokeAccessTokenAndIncrementVersion(
                jwt.getId(), jwt.getExpiresAt(), userId, tokenVersion);
    }

    private Set<String> safeRoles(Set<String> roles) {
        return roles == null ? Set.of() : Set.copyOf(roles);
    }

    private LoginResponse response(JwtTokenService.TokenPair pair) {
        return new LoginResponse(
                pair.accessToken(),
                pair.refreshToken(),
                pair.accessTokenExpiresAt(),
                pair.refreshTokenExpiresAt()
        );
    }
}
