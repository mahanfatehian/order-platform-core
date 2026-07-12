package com.orderprocessing.security.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.UUID;

/** Rejects refresh tokens and incomplete signed JWTs at every resource-server boundary. */
public final class AccessTokenClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token", "JWT does not satisfy the access-token contract", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("type"))
                || !StringUtils.hasText(jwt.getSubject())
                || !StringUtils.hasText(jwt.getId())
                || jwt.getIssuedAt() == null
                || jwt.getExpiresAt() == null
                || !validUserId(jwt.getClaimAsString("userId"))
                || !validVersion(jwt.getClaim("tokenVersion"))
                || !(jwt.getClaims().get("roles") instanceof Collection<?>)) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean validUserId(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean validVersion(Object value) {
        return value instanceof Number number && number.longValue() > 0;
    }
}
