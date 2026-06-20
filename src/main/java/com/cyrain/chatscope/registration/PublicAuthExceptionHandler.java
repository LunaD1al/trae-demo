package com.cyrain.chatscope.registration;

import com.cyrain.chatscope.openim.adapter.OpenImApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps gateway failures to stable public responses without leaking internals.
 */
@RestControllerAdvice(assignableTypes = PublicAuthController.class)
class PublicAuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PublicAuthExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<PublicError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(PublicError.of("invalid_request", ex.getMessage()));
    }

    @ExceptionHandler(OpenImApiException.class)
    ResponseEntity<PublicError> handleOpenImFailure(OpenImApiException ex) {
        log.warn("Unexpected OpenIM failure in public auth gateway", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(PublicError.of("openim_unavailable", "upstream OpenIM error"));
    }
}
