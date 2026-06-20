package com.cyrain.chatscope.abuse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable one-way hashing for abuse subjects and audit records.
 *
 * <p>Emails, phone numbers, IPs and device fingerprints are stored only as SHA-256 hex digests so
 * the audit tables never hold the plaintext target.
 */
public final class Hashing {

    private Hashing() {
    }

    /** SHA-256 hex digest of the given value, or {@code null} when the input is null/blank. */
    public static String sha256Hex(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
