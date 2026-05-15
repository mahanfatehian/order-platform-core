package com.orderprocessing.security.service;

import java.time.Instant;

public interface TokenBlacklistService {

    void blacklistAccessToken(String jti, Instant expiresAt);

    void blacklistRefreshToken(String jti, Instant expiresAt);

    boolean isAccessTokenBlacklisted(String jti);

    boolean isRefreshTokenBlacklisted(String jti);
}
