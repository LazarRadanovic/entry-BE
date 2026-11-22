package com.entry.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    // RFC 8291, Section 5 example
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED_BODY = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6"
            + "e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void matchesRfc8291Example() throws Exception {
        KeyPair asKeys = new KeyPair(WebPushCrypto.decodePublic(B64.decode(AS_PUBLIC)),
                WebPushCrypto.decodePrivate(B64.decode(AS_PRIVATE)));

        byte[] body = WebPushCrypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8), B64.decode(UA_PUBLIC),
                B64.decode(AUTH_SECRET), asKeys, B64.decode(SALT));

        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(body)).isEqualTo(EXPECTED_BODY);
    }

    @Test
    void userAgentCanDecryptRandomisedMessage() throws Exception {
        byte[] body = WebPushCrypto.encrypt("hello neighbours".getBytes(StandardCharsets.UTF_8),
                B64.decode(UA_PUBLIC), B64.decode(AUTH_SECRET));

        assertThat(new String(decrypt(body), StandardCharsets.UTF_8)).isEqualTo("hello neighbours");
    }

    /** Receiver side of RFC 8291, written independently of the production code. */
    private static byte[] decrypt(byte[] body) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buf.get(salt);
        buf.getInt();
        byte[] asPublic = new byte[buf.get()];
        buf.get(asPublic);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        ECPrivateKey uaPrivate = WebPushCrypto.decodePrivate(B64.decode(UA_PRIVATE));
        ECPublicKey sender = WebPushCrypto.decodePublic(asPublic);
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(uaPrivate);
        ka.doPhase(sender, true);
        byte[] ecdh = ka.generateSecret();

        byte[] uaPublic = B64.decode(UA_PUBLIC);
        byte[] info = ByteBuffer.allocate(14 + 65 + 65).put("WebPush: info\0".getBytes(StandardCharsets.US_ASCII))
                .put(uaPublic).put(asPublic).array();
        byte[] ikm = expand(hmac(B64.decode(AUTH_SECRET), ecdh), info, 32);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = c.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] expand(byte[] prk, byte[] info, int len) throws Exception {
        byte[] withCounter = Arrays.copyOf(info, info.length + 1);
        withCounter[info.length] = 1;
        return Arrays.copyOf(hmac(prk, withCounter), len);
    }
}
