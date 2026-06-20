package com.cyrain.chatscope.registration;

import java.util.Set;

import com.cyrain.chatscope.abuse.AbuseGuardService;
import com.cyrain.chatscope.abuse.AbuseProperties;
import com.cyrain.chatscope.abuse.GuardDecision;
import com.cyrain.chatscope.membership.EnsureMembershipResponse;
import com.cyrain.chatscope.membership.MembershipService;
import com.cyrain.chatscope.openim.adapter.OpenImApiException;
import com.cyrain.chatscope.openim.adapter.OpenImChatAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Public Demo Registration Gateway.
 *
 * <p>The single guarded entry point for verification-code sends and public registration. Every
 * request runs through Abuse Guard <em>before</em> any OpenIM ChatServer call, so blocked or
 * challenged requests never spend SMS/SMTP or hit OpenIM. Every outcome is written to the audit
 * tables. On successful registration it ensures the new user joins the single Demo group.
 */
@Service
public class RegistrationGatewayService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationGatewayService.class);

    /** OpenIM ChatServer errCodes that mean the supplied verification code was wrong/expired/used. */
    private static final Set<Integer> VERIFY_CODE_ERRORS = Set.of(20006, 20007, 20008);
    private static final int REGISTER_PLATFORM = 5;

    private final AbuseGuardService guard;
    private final AbuseProperties abuseProperties;
    private final OpenImChatAdapter openImChat;
    private final VerificationEventRepository verificationEvents;
    private final RegistrationEventRepository registrationEvents;
    private final MembershipService membershipService;

    RegistrationGatewayService(
            AbuseGuardService guard,
            AbuseProperties abuseProperties,
            OpenImChatAdapter openImChat,
            VerificationEventRepository verificationEvents,
            RegistrationEventRepository registrationEvents,
            MembershipService membershipService) {
        this.guard = guard;
        this.abuseProperties = abuseProperties;
        this.openImChat = openImChat;
        this.verificationEvents = verificationEvents;
        this.registrationEvents = registrationEvents;
        this.membershipService = membershipService;
    }

    GatewayResult<CodeSendResponse> sendCode(CodeSendRequest request, ClientNetwork net) {
        String channel = requireChannel(request.channel());
        ContactTarget target = ContactTarget.parse(channel, request.target());

        GuardDecision decision = guard.checkCodeSend(
                channel, target.subjectType(), target.normalized(), net.ip(), net.cidr(), request.deviceFingerprint());

        if (decision.type() == GuardDecision.Type.BLOCKED) {
            recordVerification(channel, target, net, request, "blocked", decision.reason());
            return GatewayResult.of(429, CodeSendResponse.blocked(decision.reason(), decision.retryAfterSeconds()));
        }
        if (decision.type() == GuardDecision.Type.CHALLENGE_REQUIRED) {
            recordVerification(channel, target, net, request, "challenged", decision.reason());
            return GatewayResult.of(200, CodeSendResponse.challenge(decision.challengeType()));
        }

        try {
            openImChat.sendVerificationCode(
                    target.areaCode(), target.phoneNumber(), target.email(), OpenImChatAdapter.USED_FOR_REGISTER);
        } catch (OpenImApiException ex) {
            log.warn("OpenIM verification-code send failed for channel={}", channel, ex);
            recordVerification(channel, target, net, request, "provider_failed", "openim_send_failed");
            return GatewayResult.of(502, CodeSendResponse.providerFailed("openim_send_failed"));
        }

        guard.commitCodeSent(channel, target.normalized());
        recordVerification(channel, target, net, request, "allowed", null);
        return GatewayResult.of(200, CodeSendResponse.allowed(abuseProperties.codeResendCooldownSeconds()));
    }

    GatewayResult<?> register(RegisterRequest request, ClientNetwork net) {
        String channel = requireChannel(request.channel());
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (request.verifyCode() == null || request.verifyCode().isBlank()) {
            throw new IllegalArgumentException("verify_code is required");
        }
        ContactTarget target = ContactTarget.parse(channel, request.target());

        GuardDecision decision = guard.checkRegistration(
                net.ip(), net.cidr(), request.deviceFingerprint(), target.subjectType(), target.normalized());
        if (decision.type() != GuardDecision.Type.ALLOWED) {
            recordRegistration(null, target, net, request, "blocked", decision.reason());
            return GatewayResult.of(429, PublicError.of("blocked", decision.reason(), decision.retryAfterSeconds()));
        }

        String userId;
        try {
            userId = openImChat.register(
                    request.displayName(),
                    target.areaCode(),
                    target.phoneNumber(),
                    target.email(),
                    Passwords.md5Hex(request.password()),
                    request.verifyCode(),
                    REGISTER_PLATFORM,
                    deviceId(request.deviceFingerprint()));
        } catch (OpenImApiException ex) {
            if (VERIFY_CODE_ERRORS.contains(ex.errCode())) {
                guard.recordVerifyFailure(target.normalized());
                recordRegistration(null, target, net, request, "failed", "verify_failed");
                return GatewayResult.of(400, PublicError.of("verify_failed", "verify code not match or expired"));
            }
            log.warn("OpenIM registration failed errCode={}", ex.errCode(), ex);
            recordRegistration(null, target, net, request, "failed", "openim_register_failed");
            return GatewayResult.of(502, PublicError.of("openim_register_failed", "registration rejected by OpenIM"));
        }

        EnsureMembershipResponse membership = membershipService.ensure(userId);
        recordRegistration(userId, target, net, request, "allowed", null);
        log.info("Registered {} via channel={} and ensured demo membership", userId, channel);
        return GatewayResult.of(200, new RegisterResponse(userId, membership.groupId(), membership.quota()));
    }

    private static String requireChannel(String channel) {
        if (!"sms".equals(channel) && !"smtp".equals(channel)) {
            throw new IllegalArgumentException("channel must be sms or smtp");
        }
        return channel;
    }

    private static String deviceId(String deviceFingerprint) {
        return (deviceFingerprint == null || deviceFingerprint.isBlank()) ? "chatscope-web" : deviceFingerprint;
    }

    private void recordVerification(
            String channel, ContactTarget target, ClientNetwork net, CodeSendRequest req,
            String decision, String reason) {
        verificationEvents.record(
                channel, target.normalized(), net.ip(), net.cidr(), req.deviceFingerprint(),
                "send_code", decision, reason);
    }

    private void recordRegistration(
            String userId, ContactTarget target, ClientNetwork net, RegisterRequest req,
            String decision, String reason) {
        String phone = "phone".equals(target.subjectType()) ? target.normalized() : null;
        registrationEvents.record(
                userId, target.email(), phone, net.ip(), net.cidr(),
                req.deviceFingerprint(), decision, reason);
    }
}
