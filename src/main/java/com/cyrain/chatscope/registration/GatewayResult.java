package com.cyrain.chatscope.registration;

/**
 * Carries the HTTP status the gateway decided on together with the response body, so the gateway
 * service can own the full decision (allow / block / challenge / provider failure) without depending
 * on Servlet types.
 */
record GatewayResult<T>(int status, T body) {

    static <T> GatewayResult<T> of(int status, T body) {
        return new GatewayResult<>(status, body);
    }
}
