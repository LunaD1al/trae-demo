package com.cyrain.chatscope.registration;

import com.cyrain.chatscope.quota.QuotaOverview;

/**
 * Success response for {@code POST /api/public/auth/register}.
 */
public record RegisterResponse(String openimUserId, String groupId, QuotaOverview quota) {
}
