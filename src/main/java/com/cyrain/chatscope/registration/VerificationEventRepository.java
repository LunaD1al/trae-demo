package com.cyrain.chatscope.registration;

import com.cyrain.chatscope.abuse.Hashing;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Audit writer for verification-code send/verify events.
 *
 * <p>Targets, IPs and device fingerprints are stored only as hashes — never plaintext.
 */
@Repository
class VerificationEventRepository {

    private final JdbcClient jdbcClient;

    VerificationEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void record(
            String channel,
            String target,
            String ip,
            String ipCidr,
            String deviceFingerprint,
            String action,
            String decision,
            String reason) {
        jdbcClient.sql("""
                insert into verification_events
                  (channel, target_hash, ip_hash, ip_cidr_hash, device_fingerprint_hash,
                   action, decision, reason)
                values
                  (:channel, :targetHash, :ipHash, :cidrHash, :deviceHash,
                   :action, :decision, :reason)
                """)
                .param("channel", channel)
                .param("targetHash", Hashing.sha256Hex(target))
                .param("ipHash", Hashing.sha256Hex(ip))
                .param("cidrHash", Hashing.sha256Hex(ipCidr))
                .param("deviceHash", Hashing.sha256Hex(deviceFingerprint))
                .param("action", action)
                .param("decision", decision)
                .param("reason", reason)
                .update();
    }
}
