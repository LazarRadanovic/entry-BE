package com.entry.file;

import com.entry.common.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * {@code <img src>} cannot send an Authorization header, so image URLs carry an HMAC signature
 * and an expiry instead: {@code /api/files/{id}?variant=thumb&exp=...&sig=...}.
 * The expiry is rounded to the start of a UTC day so a URL stays identical for a whole day
 * (browser and service-worker caches keep working).
 */
@Component
public class FileUrlSigner {

    public enum Variant { full, thumb }

    private final SecretKeySpec key;
    private final Duration ttl;

    public FileUrlSigner(AppProperties props) {
        // Separate key derived from the JWT secret, so a file signature can never be used as a token signature.
        byte[] secret = Base64.getDecoder().decode(props.jwt().secret());
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(secret, "HmacSHA256"), "file-url-signing"), "HmacSHA256");
        this.ttl = props.storage().urlTtl();
    }

    /** Returns null for a null id, which is handy for optional avatars. */
    public String url(UUID fileId, Variant variant) {
        if (fileId == null) {
            return null;
        }
        long exp = Instant.now().plus(ttl).truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS).getEpochSecond();
        return "/api/files/" + fileId + "?variant=" + variant + "&exp=" + exp + "&sig=" + sign(fileId, variant, exp);
    }

    public String thumbUrl(UUID fileId) {
        return url(fileId, Variant.thumb);
    }

    public String fullUrl(UUID fileId) {
        return url(fileId, Variant.full);
    }

    public boolean verify(UUID fileId, Variant variant, long exp, String sig) {
        if (sig == null || Instant.now().getEpochSecond() > exp) {
            return false;
        }
        return MessageDigest.isEqual(sign(fileId, variant, exp).getBytes(StandardCharsets.US_ASCII),
                sig.getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(UUID fileId, Variant variant, long exp) {
        byte[] mac = hmac(key, fileId + ":" + variant + ":" + exp);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
    }

    private static byte[] hmac(SecretKeySpec key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
