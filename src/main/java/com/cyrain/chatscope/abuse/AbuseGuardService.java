package com.cyrain.chatscope.abuse;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.cyrain.chatscope.abuse.AbuseProperties.ChannelLimits;
import com.cyrain.chatscope.abuse.AbuseProperties.RegistrationLimits;

import org.springframework.stereotype.Service;

/**
 * Low-cost abuse checks for the public Demo entry points.
 *
 * <p>Every verification-code and registration request passes through here before any OpenIM call.
 * Rate counters live in Redis; durable blocks/allowlists live in {@code abuse_subjects}. The global
 * SMS/SMTP daily budget is only consumed once a send actually succeeds, so blocked attempts never
 * burn budget.
 */
@Service
public class AbuseGuardService {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration ONE_DAY = Duration.ofDays(1);
    private static final String DEFAULT_CHALLENGE = "turnstile";

    private final AbuseProperties properties;
    private final RateLimiter rateLimiter;
    private final AbuseSubjectRepository abuseSubjects;

    AbuseGuardService(AbuseProperties properties, RateLimiter rateLimiter, AbuseSubjectRepository abuseSubjects) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.abuseSubjects = abuseSubjects;
    }

    /**
     * Guard a verification-code send. Increments the per-IP/target/CIDR counters (counting the
     * attempt) and peeks the global budget. The caller must invoke {@link #commitCodeSent} only after
     * OpenIM accepts the send.
     */
    public GuardDecision checkCodeSend(
            String channel, String targetType, String target, String ip, String ipCidr, String deviceFingerprint) {
        ChannelLimits limits = "sms".equals(channel) ? properties.sms() : properties.smtp();

        String ipHash = Hashing.sha256Hex(ip);
        String cidrHash = Hashing.sha256Hex(ipCidr);
        String targetHash = Hashing.sha256Hex(target);
        String deviceHash = Hashing.sha256Hex(deviceFingerprint);

        SubjectStatus subject = resolveSubjects(ipHash, cidrHash, targetType, targetHash, deviceHash);
        if (subject == SubjectStatus.BLOCKED) {
            return GuardDecision.blocked("subject_blocked", null);
        }
        if (subject == SubjectStatus.ALLOWLISTED) {
            return GuardDecision.allowed();
        }

        if (rateLimiter.markerExists("cooldown:" + channel + ":" + targetHash)) {
            return GuardDecision.blocked("code_resend_cooldown", properties.codeResendCooldownSeconds());
        }

        if (rateLimiter.isAtOrOverLimit("budget:" + channel + ":" + today(), limits.globalDailyBudget())) {
            return GuardDecision.blocked(channel + "_daily_budget_exceeded", (int) ONE_DAY.toSeconds());
        }

        // Per-IP minute window is the soft threshold: serve a challenge instead of a hard block.
        if (!rateLimiter.incrementAndCheck(channel + ":ip:min:" + ip, limits.perIpPerMinute(), ONE_MINUTE)) {
            return softThreshold("ip_minute_rate_limited", ONE_MINUTE);
        }
        if (!rateLimiter.incrementAndCheck(channel + ":ip:hour:" + ip, limits.perIpPerHour(), ONE_HOUR)) {
            return GuardDecision.blocked("ip_hourly_rate_limited", (int) ONE_HOUR.toSeconds());
        }
        if (!rateLimiter.incrementAndCheck(channel + ":target:hour:" + targetHash, limits.perTargetPerHour(), ONE_HOUR)) {
            return GuardDecision.blocked("target_hourly_rate_limited", (int) ONE_HOUR.toSeconds());
        }
        if (!rateLimiter.incrementAndCheck(channel + ":target:day:" + targetHash, limits.perTargetPerDay(), ONE_DAY)) {
            return GuardDecision.blocked("target_daily_rate_limited", (int) ONE_DAY.toSeconds());
        }
        if (cidrHash != null
                && !rateLimiter.incrementAndCheck(channel + ":cidr:day:" + cidrHash, limits.perCidrPerDay(), ONE_DAY)) {
            return GuardDecision.blocked("cidr_daily_rate_limited", (int) ONE_DAY.toSeconds());
        }
        return GuardDecision.allowed();
    }

    /** Consume the cooldown marker and global daily budget after a successful send. */
    public void commitCodeSent(String channel, String target) {
        String targetHash = Hashing.sha256Hex(target);
        rateLimiter.setMarker(
                "cooldown:" + channel + ":" + targetHash, Duration.ofSeconds(properties.codeResendCooldownSeconds()));
        rateLimiter.increment("budget:" + channel + ":" + today(), ONE_DAY);
    }

    /** Guard a registration attempt before calling the OpenIM ChatServer register API. */
    public GuardDecision checkRegistration(
            String ip, String ipCidr, String deviceFingerprint, String targetType, String target) {
        RegistrationLimits limits = properties.registration();

        String ipHash = Hashing.sha256Hex(ip);
        String cidrHash = Hashing.sha256Hex(ipCidr);
        String targetHash = Hashing.sha256Hex(target);
        String deviceHash = Hashing.sha256Hex(deviceFingerprint);

        SubjectStatus subject = resolveSubjects(ipHash, cidrHash, targetType, targetHash, deviceHash);
        if (subject == SubjectStatus.BLOCKED) {
            return GuardDecision.blocked("subject_blocked", null);
        }
        if (subject == SubjectStatus.ALLOWLISTED) {
            return GuardDecision.allowed();
        }

        if (rateLimiter.markerExists("reg:verifyfail:lock:" + targetHash)) {
            return GuardDecision.blocked("verify_failure_locked", limits.verifyFailureLockMinutes() * 60);
        }
        if (!rateLimiter.incrementAndCheck("reg:ip:hour:" + ip, limits.perIpPerHour(), ONE_HOUR)) {
            return GuardDecision.blocked("registration_ip_hourly_limited", (int) ONE_HOUR.toSeconds());
        }
        if (!rateLimiter.incrementAndCheck("reg:ip:day:" + ip, limits.perIpPerDay(), ONE_DAY)) {
            return GuardDecision.blocked("registration_ip_daily_limited", (int) ONE_DAY.toSeconds());
        }
        if (cidrHash != null
                && !rateLimiter.incrementAndCheck("reg:cidr:day:" + cidrHash, limits.perCidrPerDay(), ONE_DAY)) {
            return GuardDecision.blocked("registration_cidr_daily_limited", (int) ONE_DAY.toSeconds());
        }
        return GuardDecision.allowed();
    }

    /**
     * Record a verification failure for the target. After {@code verifyFailureLockThreshold} failures
     * within the lock window, the target is locked for {@code verifyFailureLockMinutes}.
     */
    public void recordVerifyFailure(String target) {
        RegistrationLimits limits = properties.registration();
        String targetHash = Hashing.sha256Hex(target);
        Duration lockWindow = Duration.ofMinutes(limits.verifyFailureLockMinutes());
        long failures = rateLimiter.increment("reg:verifyfail:count:" + targetHash, lockWindow);
        if (failures >= limits.verifyFailureLockThreshold()) {
            rateLimiter.setMarker("reg:verifyfail:lock:" + targetHash, lockWindow);
        }
    }

    private GuardDecision softThreshold(String reason, Duration retryAfter) {
        if (properties.challengeEnabled()) {
            return GuardDecision.challenge(DEFAULT_CHALLENGE);
        }
        return GuardDecision.blocked(reason, (int) retryAfter.toSeconds());
    }

    private SubjectStatus resolveSubjects(
            String ipHash, String cidrHash, String targetType, String targetHash, String deviceHash) {
        boolean allowlisted = false;
        for (SubjectStatus status : new SubjectStatus[] {
                statusOf("ip", ipHash),
                statusOf("ip_cidr", cidrHash),
                statusOf(targetType, targetHash),
                statusOf("device_fingerprint", deviceHash)}) {
            if (status == SubjectStatus.BLOCKED) {
                return SubjectStatus.BLOCKED;
            }
            if (status == SubjectStatus.ALLOWLISTED) {
                allowlisted = true;
            }
        }
        return allowlisted ? SubjectStatus.ALLOWLISTED : SubjectStatus.NONE;
    }

    private SubjectStatus statusOf(String subjectType, String valueHash) {
        return abuseSubjects.findActiveStatus(subjectType, valueHash)
                .map(status -> switch (status) {
                    case "blocked" -> SubjectStatus.BLOCKED;
                    case "allowlisted" -> SubjectStatus.ALLOWLISTED;
                    default -> SubjectStatus.NONE;
                })
                .orElse(SubjectStatus.NONE);
    }

    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    private enum SubjectStatus {
        NONE,
        BLOCKED,
        ALLOWLISTED
    }
}
