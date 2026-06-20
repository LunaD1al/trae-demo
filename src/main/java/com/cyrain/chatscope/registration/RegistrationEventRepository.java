package com.cyrain.chatscope.registration;

import com.cyrain.chatscope.abuse.Hashing;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Audit writer for public registration attempts.
 */
@Repository
class RegistrationEventRepository {

    private final JdbcClient jdbcClient;

    RegistrationEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void record(
            String openimUserId,
            String email,
            String phone,
            String ip,
            String ipCidr,
            String deviceFingerprint,
            String decision,
            String reason) {
        jdbcClient.sql("""
                insert into registration_events
                  (openim_user_id, email_hash, phone_hash, ip_hash, ip_cidr_hash,
                   device_fingerprint_hash, decision, reason)
                values
                  (:userId, :emailHash, :phoneHash, :ipHash, :cidrHash,
                   :deviceHash, :decision, :reason)
                """)
                .param("userId", openimUserId)
                .param("emailHash", Hashing.sha256Hex(email))
                .param("phoneHash", Hashing.sha256Hex(phone))
                .param("ipHash", Hashing.sha256Hex(ip))
                .param("cidrHash", Hashing.sha256Hex(ipCidr))
                .param("deviceHash", Hashing.sha256Hex(deviceFingerprint))
                .param("decision", decision)
                .param("reason", reason)
                .update();
    }
}
