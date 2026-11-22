package com.entry.entrance;

import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Short, human-friendly invite codes (no 0/O/1/I to avoid confusion when typed). */
@Component
@RequiredArgsConstructor
public class InviteCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 8;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final EntranceRepository entrances;

    public String generateUnique() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String code = generate();
            if (!entrances.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique invite code");
    }

    private String generate() {
        char[] out = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }
}
