package com.cyrain.chatscope.registration;

/**
 * Generic error body for the public auth endpoints. Null fields are omitted.
 */
record PublicError(String error, String reason, Integer retryAfterSeconds) {

    static PublicError of(String error, String reason) {
        return new PublicError(error, reason, null);
    }

    static PublicError of(String error, String reason, Integer retryAfterSeconds) {
        return new PublicError(error, reason, retryAfterSeconds);
    }
}
