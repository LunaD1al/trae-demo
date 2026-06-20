package com.cyrain.chatscope.membership;

import java.util.Map;

import com.cyrain.chatscope.openim.adapter.OpenImApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps membership failures to stable HTTP responses.
 */
@RestControllerAdvice(assignableTypes = MembershipController.class)
class MembershipExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MembershipExceptionHandler.class);

    @ExceptionHandler(OpenImUserNotFoundException.class)
    ResponseEntity<Map<String, String>> handleUserNotFound(OpenImUserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "openim_user_not_found", "message", ex.getMessage()));
    }

    @ExceptionHandler(OpenImApiException.class)
    ResponseEntity<Map<String, String>> handleOpenImFailure(OpenImApiException ex) {
        log.warn("OpenIM call failed while ensuring membership", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "openim_unavailable", "message", ex.getMessage()));
    }
}
