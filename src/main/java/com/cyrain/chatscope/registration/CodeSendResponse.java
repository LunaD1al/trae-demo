package com.cyrain.chatscope.registration;

/**
 * Response for {@code POST /api/public/auth/code/send}.
 *
 * <p>Only the fields relevant to the decision are serialised (null fields are omitted globally via
 * Jackson non-null inclusion): {@code cooldown_seconds} on allow, {@code reason}/
 * {@code retry_after_seconds} on block, {@code challenge_type} on challenge.
 */
public record CodeSendResponse(
        String decision,
        Integer cooldownSeconds,
        String reason,
        Integer retryAfterSeconds,
        String challengeType) {

    static CodeSendResponse allowed(int cooldownSeconds) {
        return new CodeSendResponse("allowed", cooldownSeconds, null, null, null);
    }

    static CodeSendResponse blocked(String reason, Integer retryAfterSeconds) {
        return new CodeSendResponse("blocked", null, reason, retryAfterSeconds, null);
    }

    static CodeSendResponse challenge(String challengeType) {
        return new CodeSendResponse("challenge_required", null, null, null, challengeType);
    }

    static CodeSendResponse providerFailed(String reason) {
        return new CodeSendResponse("provider_failed", null, reason, null, null);
    }
}
