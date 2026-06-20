package com.cyrain.chatscope.registration;

/**
 * Request body for {@code POST /api/public/auth/code/send}.
 *
 * @param channel           {@code sms} or {@code smtp}
 * @param target            phone number (E.164) for SMS, email address for SMTP
 * @param purpose           client-declared purpose (e.g. {@code register}); informational
 * @param deviceFingerprint browser-generated fingerprint used as an abuse dimension
 */
public record CodeSendRequest(String channel, String target, String purpose, String deviceFingerprint) {
}
