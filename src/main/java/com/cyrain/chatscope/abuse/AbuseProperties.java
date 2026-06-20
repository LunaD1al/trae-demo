package com.cyrain.chatscope.abuse;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Abuse Guard thresholds for the public Demo entry points.
 *
 * <p>Mirrors the {@code chatscope.abuse} block in the backend spec: per-channel verification-code
 * rate limits and global daily budgets, plus registration rate limits and verify-failure locking.
 */
@ConfigurationProperties(prefix = "chatscope.abuse")
public record AbuseProperties(
        boolean challengeEnabled,
        int codeResendCooldownSeconds,
        ChannelLimits sms,
        ChannelLimits smtp,
        RegistrationLimits registration,
        AiLimits ai) {

    /** Verification-code limits for a single channel (SMS or SMTP). */
    public record ChannelLimits(
            int globalDailyBudget,
            int perIpPerMinute,
            int perIpPerHour,
            int perTargetPerHour,
            int perTargetPerDay,
            int perCidrPerDay) {
    }

    public record RegistrationLimits(
            int perIpPerHour,
            int perIpPerDay,
            int perCidrPerDay,
            int verifyFailureLockThreshold,
            int verifyFailureLockMinutes) {
    }

    public record AiLimits(int perIpPerMinute, int perUserPerMinute) {
    }
}
