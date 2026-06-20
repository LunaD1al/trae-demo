package com.cyrain.chatscope.membership;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Membership API for the single Demo group.
 */
@RestController
@RequestMapping("/api/demo/membership")
class MembershipController {

    private final MembershipService membershipService;

    MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping(path = "/ensure", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    EnsureMembershipResponse ensure(@RequestBody EnsureMembershipRequest request) {
        if (request == null || request.openimUserId() == null || request.openimUserId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "openim_user_id is required");
        }
        return membershipService.ensure(request.openimUserId().trim());
    }
}
