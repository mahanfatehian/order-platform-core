package com.orderprocessing.security.config;

import jakarta.annotation.Nonnull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class JwtAuthenticationConverterImpl implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthoritiesConverter authoritiesConverter = new JwtAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(@Nonnull Jwt jwt) {
        return new JwtAuthenticationToken(
                jwt,
                authoritiesConverter.convert(jwt),
                jwt.getSubject()
        );
    }
}
