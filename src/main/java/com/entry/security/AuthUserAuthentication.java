package com.entry.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Authorities: {@code ROLE_RESIDENT | ROLE_ENTRANCE_ADMIN} plus {@code STATUS_PENDING | STATUS_ACTIVE | ...}.
 */
public class AuthUserAuthentication extends AbstractAuthenticationToken {

    private final AuthUser principal;
    private final String token;

    public AuthUserAuthentication(AuthUser principal, String token) {
        super(List.of(
                new SimpleGrantedAuthority("ROLE_" + principal.role().name()),
                new SimpleGrantedAuthority("STATUS_" + principal.status().name())));
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public AuthUser getPrincipal() {
        return principal;
    }

    @Override
    public String getCredentials() {
        return token;
    }

    @Override
    public String getName() {
        return principal.id().toString();
    }
}
