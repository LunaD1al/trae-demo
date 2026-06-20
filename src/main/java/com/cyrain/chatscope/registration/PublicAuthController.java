package com.cyrain.chatscope.registration;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Demo auth gateway: the only public path to OpenIM verification-code send and registration.
 */
@RestController
@RequestMapping(path = "/api/public/auth", produces = MediaType.APPLICATION_JSON_VALUE)
class PublicAuthController {

    private final RegistrationGatewayService gatewayService;

    PublicAuthController(RegistrationGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping(path = "/code/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CodeSendResponse> sendCode(
            @RequestBody CodeSendRequest request, HttpServletRequest httpRequest) {
        GatewayResult<CodeSendResponse> result = gatewayService.sendCode(request, ClientNetwork.from(httpRequest));
        return ResponseEntity.status(result.status()).body(result.body());
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        GatewayResult<?> result = gatewayService.register(request, ClientNetwork.from(httpRequest));
        return ResponseEntity.status(result.status()).body(result.body());
    }
}
