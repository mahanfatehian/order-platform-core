package com.orderprocessing.security.service;

import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Coordinates the Redis state used for individual-token and user-wide revocation.
 * Implementations must make refresh rotation and logout single-winner operations.
 */
public interface TokenRevocationService extends TokenBlacklistService {

    long getOrCreateTokenVersion(UUID userId);

    OptionalLong getTokenVersion(UUID userId);

    long incrementTokenVersion(UUID userId);

    boolean rotateRefreshToken(
            String refreshJti,
            Instant refreshExpiresAt,
            String linkedAccessJti,
            Instant linkedAccessExpiresAt,
            UUID userId,
            long expectedTokenVersion
    );

    boolean revokeAccessTokenAndIncrementVersion(
            String accessJti,
            Instant accessExpiresAt,
            UUID userId,
            long expectedTokenVersion
    );
}
