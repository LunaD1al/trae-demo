package com.cyrain.chatscope.registration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Password hashing for OpenIM ChatServer.
 *
 * <p>ChatServer stores MD5-hashed passwords (matching its own client SDK). ChatScope computes the
 * digest in transit and never persists the plaintext or the hash.
 */
final class Passwords {

    private Passwords() {
    }

    static String md5Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 not available", ex);
        }
    }
}
