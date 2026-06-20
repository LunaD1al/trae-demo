package com.cyrain.chatscope.abuse;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read access to long-term abuse subject status in PostgreSQL.
 *
 * <p>Redis holds the live rate-limit counters; this table holds durable, human-confirmed states
 * (blocked / watch / allowlisted) keyed by hashed subject value.
 */
@Repository
public class AbuseSubjectRepository {

    private final JdbcClient jdbcClient;

    AbuseSubjectRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Current status for a subject, considering expiry. Returns empty when there is no active record.
     */
    public Optional<String> findActiveStatus(String subjectType, String subjectValueHash) {
        if (subjectValueHash == null) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
                select status
                from abuse_subjects
                where subject_type = :type
                  and subject_value_hash = :hash
                  and (expires_at is null or expires_at > now())
                """)
                .param("type", subjectType)
                .param("hash", subjectValueHash)
                .query(String.class)
                .optional();
    }
}
