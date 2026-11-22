package com.entry.auth;

import com.entry.common.config.AppProperties;
import com.entry.common.exception.ApiException;
import com.entry.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, rotating refresh tokens. Only a SHA-256 hash is stored. Presenting an already-rotated
 * token is treated as theft: every session of that user is revoked.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public String create(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(user, hash(raw), Instant.now().plus(props.jwt().refreshTokenTtl())));
        return raw;
    }

    /** Validates and revokes the presented token, returning its owner. Caller then issues a new one. */
    @Transactional(noRollbackFor = ApiException.class)
    public User consume(String raw) {
        Instant now = Instant.now();
        RefreshToken token = tokens.findByTokenHash(hash(raw))
                .orElseThrow(() -> invalid());
        if (token.isRevoked()) {
            // Reuse of an already-rotated token: kill every session (committed thanks to noRollbackFor).
            tokens.revokeAllForUser(token.getUser().getId(), now);
            throw invalid();
        }
        if (token.isExpired(now)) {
            throw invalid();
        }
        token.setRevokedAt(now);
        return token.getUser();
    }

    @Transactional
    public void revoke(String raw) {
        tokens.findByTokenHash(hash(raw)).ifPresent(t -> {
            if (!t.isRevoked()) {
                t.setRevokedAt(Instant.now());
            }
        });
    }

    @Transactional
    public void revokeAll(User user) {
        tokens.revokeAllForUser(user.getId(), Instant.now());
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired, please log in again");
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
