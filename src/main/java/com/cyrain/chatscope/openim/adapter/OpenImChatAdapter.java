package com.cyrain.chatscope.openim.adapter;

import java.util.HashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

/**
 * Wrapper over the OpenIM ChatServer (chat-api) native account endpoints.
 *
 * <p>This is the only path through which ChatScope drives OpenIM's verification-code send and public
 * registration. The public Demo client must never call the ChatServer directly; it goes through the
 * ChatScope Registration Gateway, which runs Abuse Guard before delegating here.
 */
@Component
public class OpenImChatAdapter {

    /** ChatServer {@code usedFor} value for registration verification codes. */
    public static final int USED_FOR_REGISTER = 1;

    private final OpenImRestClient restClient;
    private final OpenImProperties properties;

    OpenImChatAdapter(OpenImRestClient restClient, OpenImProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * Trigger OpenIM's native verification-code send (SMS or email). OpenIM performs the actual
     * SMS/SMTP delivery via its configured provider. Throws {@link OpenImApiException} on failure.
     */
    public void sendVerificationCode(String areaCode, String phoneNumber, String email, int usedFor) {
        Map<String, Object> body = new HashMap<>();
        body.put("usedFor", usedFor);
        if (email != null && !email.isBlank()) {
            body.put("email", email);
        } else {
            body.put("areaCode", areaCode);
            body.put("phoneNumber", phoneNumber);
        }
        restClient.post(properties.chatApiBaseUrl(), "/account/code/send", null, body);
    }

    /**
     * Register a user through the ChatServer with a verification code. Returns the new OpenIM user id.
     * The password must already be MD5-hashed (ChatServer never receives plaintext from ChatScope).
     */
    public String register(
            String nickname,
            String areaCode,
            String phoneNumber,
            String email,
            String passwordMd5,
            String verifyCode,
            int platform,
            String deviceId) {
        Map<String, Object> user = new HashMap<>();
        user.put("nickname", nickname);
        user.put("password", passwordMd5);
        if (email != null && !email.isBlank()) {
            user.put("email", email);
        } else {
            user.put("areaCode", areaCode);
            user.put("phoneNumber", phoneNumber);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user", user);
        body.put("verifyCode", verifyCode);
        body.put("platform", platform);
        body.put("autoLogin", false);
        body.put("deviceID", deviceId);

        JsonNode data = restClient.post(properties.chatApiBaseUrl(), "/account/register", null, body);
        return data.path("userID").asString();
    }
}
