package com.cyrain.chatscope.quota;

import java.time.LocalDate;

import com.cyrain.chatscope.config.AiProperties;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily AI request quota, sourced from PostgreSQL.
 *
 * <p>This module owns the full consume/refund/admin lifecycle in later work. For the membership
 * milestone it only needs to initialise a user's quota account for the current day and report the
 * resulting overview, so that {@code /api/demo/membership/ensure} can return a quota summary.
 */
@Service
public class QuotaService {

    private final JdbcClient jdbcClient;
    private final AiProperties aiProperties;

    QuotaService(JdbcClient jdbcClient, AiProperties aiProperties) {
        this.jdbcClient = jdbcClient;
        this.aiProperties = aiProperties;
    }

    /**
     * Ensure today's quota account exists for the user (creating it with the configured default
     * daily limit if absent) and return the current overview. Idempotent.
     */
    @Transactional
    public QuotaOverview ensureDailyAccount(String openimUserId) {
        LocalDate quotaDate = LocalDate.now(aiProperties.quotaZoneId());
        jdbcClient.sql("""
                insert into ai_quota_accounts (openim_user_id, quota_date, daily_limit)
                values (:userId, :quotaDate, :dailyLimit)
                on conflict (openim_user_id, quota_date) do nothing
                """)
                .param("userId", openimUserId)
                .param("quotaDate", quotaDate)
                .param("dailyLimit", aiProperties.defaultDailyLimit())
                .update();

        return jdbcClient.sql("""
                select daily_limit, bonus_amount, used_count
                from ai_quota_accounts
                where openim_user_id = :userId and quota_date = :quotaDate
                """)
                .param("userId", openimUserId)
                .param("quotaDate", quotaDate)
                .query((rs, rowNum) -> QuotaOverview.of(
                        quotaDate,
                        rs.getInt("daily_limit"),
                        rs.getInt("bonus_amount"),
                        rs.getInt("used_count")))
                .single();
    }
}
