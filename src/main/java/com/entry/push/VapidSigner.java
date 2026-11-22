package com.entry.push;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/** VAPID (RFC 8292): an ES256 JWT proving to the push service that this server sent the message. */
class VapidSigner {

    private final ECDSASigner signer;
    private final String publicKey;
    private final String subject;

    VapidSigner(String publicKeyB64, String privateKeyB64, String subject) {
        try {
            Base64.Decoder b64 = Base64.getUrlDecoder();
            WebPushCrypto.decodePublic(b64.decode(publicKeyB64)); // validate eagerly
            ECPrivateKey privateKey = WebPushCrypto.decodePrivate(b64.decode(privateKeyB64));
            this.signer = new ECDSASigner(privateKey);
        } catch (GeneralSecurityException | JOSEException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid VAPID keys (app.push.*)", e);
        }
        this.publicKey = publicKeyB64;
        this.subject = subject;
    }

    /** Value for the {@code Authorization} header of a push request to {@code endpoint}. */
    String authorizationHeader(String endpoint) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getAuthority();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(audience)
                .expirationTime(Date.from(Instant.now().plus(Duration.ofHours(12))))
                .subject(subject)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(),
                claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return "vapid t=" + jwt.serialize() + ", k=" + publicKey;
    }
}
