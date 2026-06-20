package com.cyrain.chatscope.abuse;

/**
 * Outcome of an Abuse Guard check.
 *
 * <p>{@code ALLOWED} lets the caller proceed to OpenIM. {@code BLOCKED} and {@code CHALLENGE_REQUIRED}
 * both stop the request before any OpenIM call, SMS/SMTP spend or Kafka publish.
 */
public record GuardDecision(Type type, String reason, String challengeType, Integer retryAfterSeconds) {

    public enum Type {
        ALLOWED,
        BLOCKED,
        CHALLENGE_REQUIRED
    }

    public static GuardDecision allowed() {
        return new GuardDecision(Type.ALLOWED, null, null, null);
    }

    public static GuardDecision blocked(String reason, Integer retryAfterSeconds) {
        return new GuardDecision(Type.BLOCKED, reason, null, retryAfterSeconds);
    }

    public static GuardDecision challenge(String challengeType) {
        return new GuardDecision(Type.CHALLENGE_REQUIRED, "challenge_required", challengeType, null);
    }

    public boolean isAllowed() {
        return type == Type.ALLOWED;
    }
}
