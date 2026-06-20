package com.cyrain.chatscope.openim.adapter;

/**
 * Raised when an OpenIM REST call fails at the transport level or returns a non-zero errCode.
 */
public class OpenImApiException extends RuntimeException {

    private final int errCode;

    public OpenImApiException(String message, int errCode) {
        super(message);
        this.errCode = errCode;
    }

    public OpenImApiException(String message, Throwable cause) {
        super(message, cause);
        this.errCode = -1;
    }

    public int errCode() {
        return errCode;
    }
}
