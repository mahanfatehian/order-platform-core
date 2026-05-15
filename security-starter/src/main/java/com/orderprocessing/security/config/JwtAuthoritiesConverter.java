package com.orderprocessing.security.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

public class JwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.stream()
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        List<String> permissions = jwt.getClaimAsStringList("authorities");
        if (permissions != null) {
            permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        Object scope = jwt.getClaims().get("scope");
        if (scope instanceof String) {
            String scopeStr = (String) scope;
            for (String item : scopeStr.split(" ")) {
                if (!item.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority(item));
                }
            }
        } else if (scope instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) scope;
            collection.stream()
                    .map(String::valueOf)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        return authorities;
    }
}