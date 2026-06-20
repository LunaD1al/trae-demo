package com.cyrain.chatscope.membership;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistence for the {@code cs_users} ChatScope-side user mapping.
 */
@Repository
class CsUserRepository {

    private final JdbcClient jdbcClient;

    CsUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Insert the user mapping if absent, otherwise touch {@code updated_at}. Idempotent. */
    void upsert(String openimUserId) {
        jdbcClient.sql("""
                insert into cs_users (openim_user_id)
                values (:userId)
                on conflict (openim_user_id) do update set updated_at = now()
                """)
                .param("userId", openimUserId)
                .update();
    }

    /** Stamp {@code joined_demo_group_at} the first time the user joins; keeps the original time after. */
    void markJoinedDemoGroup(String openimUserId) {
        jdbcClient.sql("""
                update cs_users
                set joined_demo_group_at = coalesce(joined_demo_group_at, now()),
                    updated_at = now()
                where openim_user_id = :userId
                """)
                .param("userId", openimUserId)
                .update();
    }
}
