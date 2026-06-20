package com.cyrain.chatscope.registration;

import java.util.List;

/**
 * Normalised verification target, split into the fields OpenIM ChatServer expects.
 *
 * <p>For SMS the raw E.164 string is split into an {@code areaCode} and national {@code phoneNumber};
 * for SMTP the email is lower-cased. {@code normalized} is the canonical value used for hashing,
 * cooldown and rate-limit keys. {@code subjectType} maps to the {@code abuse_subjects} taxonomy.
 */
record ContactTarget(
        String channel,
        String subjectType,
        String normalized,
        String areaCode,
        String phoneNumber,
        String email) {

    /** Common area codes, longest first so prefix matching is unambiguous. */
    private static final List<String> AREA_CODES = List.of("+852", "+853", "+886", "+86", "+81", "+65", "+44", "+1");
    private static final String DEFAULT_AREA_CODE = "+86";

    static ContactTarget parse(String channel, String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            throw new IllegalArgumentException("target is required");
        }
        if ("smtp".equals(channel)) {
            String email = rawTarget.trim().toLowerCase();
            return new ContactTarget(channel, "email", email, null, null, email);
        }
        if ("sms".equals(channel)) {
            String digits = rawTarget.replaceAll("[\\s-]", "");
            String areaCode = DEFAULT_AREA_CODE;
            String national = digits;
            if (digits.startsWith("+")) {
                areaCode = AREA_CODES.stream()
                        .filter(digits::startsWith)
                        .findFirst()
                        .orElse(DEFAULT_AREA_CODE);
                national = digits.substring(areaCode.length());
            }
            String normalized = areaCode + national;
            return new ContactTarget(channel, "phone", normalized, areaCode, national, null);
        }
        throw new IllegalArgumentException("unsupported channel: " + channel);
    }
}
