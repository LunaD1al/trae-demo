package com.cyrain.chatscope.membership;

/**
 * Raised when membership is requested for an OpenIM user id that does not exist.
 */
public class OpenImUserNotFoundException extends RuntimeException {

    public OpenImUserNotFoundException(String openimUserId) {
        super("OpenIM user not found: " + openimUserId);
    }
}
