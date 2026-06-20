package com.cyrain.chatscope.quota;

import java.time.LocalDate;

/**
 * Snapshot of a user's AI request quota for a given day.
 */
public record QuotaOverview(
        LocalDate quotaDate,
        int dailyLimit,
        int bonusAmount,
        int usedCount,
        int remainingCount) {

    public static QuotaOverview of(LocalDate quotaDate, int dailyLimit, int bonusAmount, int usedCount) {
        return new QuotaOverview(
                quotaDate, dailyLimit, bonusAmount, usedCount, dailyLimit + bonusAmount - usedCount);
    }
}
