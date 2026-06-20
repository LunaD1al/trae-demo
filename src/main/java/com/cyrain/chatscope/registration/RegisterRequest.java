package com.cyrain.chatscope.registration;

/**
 * Request body for {@code POST /api/public/auth/register}.
 *
 * @param channel           {@code sms} or {@code smtp}
 * @param target            phone number (E.164) for SMS, email address for SMTP
 * @param verifyCode        verification code the user received
 * @param password          plaintext password; hashed before leaving ChatScope, never stored
 * @param displayName       user nickname
 * @param deviceFingerprint browser-generated fingerprint used as an abuse dimension
 */
public record RegisterRequest(
        String channel,
        String target,
        String verifyCode,
        String password,
        String displayName,
        String deviceFingerprint) {
}
