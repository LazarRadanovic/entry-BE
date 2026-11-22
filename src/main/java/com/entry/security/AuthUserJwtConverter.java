package com.entry.security;

import com.entry.billing.BillingPolicy;
import com.entry.user.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/** Turns a validated JWT into an {@link AuthUserAuthentication} using the user's current DB state. */
@Component
@RequiredArgsConstructor
public class AuthUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;
    private final BillingPolicy billing;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new AuthUserAuthentication(loadPrincipal(jwt.getSubject()), jwt.getTokenValue());
    }

    public AuthUser loadPrincipal(String subject) {
        UUID id;
        try {
            id = UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Invalid token subject");
        }
        return users.findWithEntranceById(id)
                .map(u -> AuthUser.from(u, !billing.accessAllowed(u.getEntrance())))
                .orElseThrow(() -> new InvalidBearerTokenException("User no longer exists"));
    }
}
