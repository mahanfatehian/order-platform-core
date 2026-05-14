package com.orderprocessing.userservice.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class InternalApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String principal;

    public InternalApiKeyAuthenticationToken(String principal) {
        super(AuthorityUtils.createAuthorityList("ROLE_INTERNAL_SERVICE"));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
