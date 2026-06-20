package com.cyrain.chatscope.membership;

/**
 * Request body for {@code POST /api/demo/membership/ensure}.
 *
 * <p>Maps {@code openim_user_id} via the global snake_case JSON naming strategy.
 */
public record EnsureMembershipRequest(String openimUserId) {
}
