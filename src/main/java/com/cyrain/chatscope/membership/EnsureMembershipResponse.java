package com.cyrain.chatscope.membership;

import com.cyrain.chatscope.quota.QuotaOverview;

/**
 * Response body for {@code POST /api/demo/membership/ensure}.
 */
public record EnsureMembershipResponse(
        String groupId,
        String openimUserId,
        QuotaOverview quota) {
}
