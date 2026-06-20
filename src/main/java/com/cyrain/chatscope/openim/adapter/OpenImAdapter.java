package com.cyrain.chatscope.openim.adapter;

import java.util.List;
import java.util.Map;

import com.cyrain.chatscope.config.BotProperties;
import tools.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Business-level wrapper over the OpenIM REST API.
 *
 * <p>Other modules depend only on this adapter and never touch OpenIM payloads directly. It exposes
 * exactly the capabilities ChatScope needs: check whether an OpenIM user exists, check and
 * idempotently ensure Demo group membership, and send group messages as the bot.
 */
@Component
public class OpenImAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenImAdapter.class);

    /** OpenIM session type for group messages. */
    private static final int SESSION_TYPE_GROUP = 3;
    /** OpenIM content type for plain text messages. */
    private static final int CONTENT_TYPE_TEXT = 101;
    /** account_check reports this status for a registered user id. */
    private static final int ACCOUNT_STATUS_REGISTERED = 1;

    private final OpenImRestClient restClient;
    private final OpenImTokenManager tokenManager;
    private final OpenImProperties properties;
    private final BotProperties botProperties;

    OpenImAdapter(
            OpenImRestClient restClient,
            OpenImTokenManager tokenManager,
            OpenImProperties properties,
            BotProperties botProperties) {
        this.restClient = restClient;
        this.tokenManager = tokenManager;
        this.properties = properties;
        this.botProperties = botProperties;
    }

    /** Whether the given OpenIM user id is registered. */
    public boolean userExists(String openimUserId) {
        Map<String, Object> body = Map.of("checkUserIDs", List.of(openimUserId));
        JsonNode data = restClient.post(
                properties.apiBaseUrl(), "/user/account_check", tokenManager.getAdminToken(), body);
        for (JsonNode result : data.path("results")) {
            if (openimUserId.equals(result.path("userID").asString())) {
                return result.path("accountStatus").asInt(0) == ACCOUNT_STATUS_REGISTERED;
            }
        }
        return false;
    }

    /** Whether the user is already a member of the given group. */
    public boolean isGroupMember(String groupId, String openimUserId) {
        Map<String, Object> body = Map.of("groupID", groupId, "userIDs", List.of(openimUserId));
        JsonNode data = restClient.post(
                properties.apiBaseUrl(), "/group/get_group_members_info", tokenManager.getAdminToken(), body);
        for (JsonNode member : data.path("members")) {
            if (openimUserId.equals(member.path("userID").asString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Idempotently ensure the user is a member of the group.
     *
     * <p>Membership is checked first so a repeat call is a no-op. If an invite is still needed and
     * races another caller, the post-invite re-check absorbs the resulting "already in group" error.
     */
    public void ensureUserInGroup(String groupId, String openimUserId) {
        if (isGroupMember(groupId, openimUserId)) {
            return;
        }
        Map<String, Object> body = Map.of(
                "groupID", groupId,
                "invitedUserIDs", List.of(openimUserId),
                "reason", "chatscope demo membership");
        try {
            restClient.post(
                    properties.apiBaseUrl(), "/group/invite_user_to_group", tokenManager.getAdminToken(), body);
        } catch (OpenImApiException ex) {
            if (isGroupMember(groupId, openimUserId)) {
                log.debug("Invite for {} into {} raced and is already a member", openimUserId, groupId);
                return;
            }
            throw ex;
        }
    }

    /**
     * Send a plain text message into the group as the ChatScope bot. Returns the OpenIM
     * {@code clientMsgID} of the sent message.
     */
    public String sendGroupTextAsBot(String groupId, String text) {
        Map<String, Object> body = Map.of(
                "sendID", botProperties.openimUserId(),
                "recvID", groupId,
                "groupID", groupId,
                "content", Map.of("content", text),
                "contentType", CONTENT_TYPE_TEXT,
                "sessionType", SESSION_TYPE_GROUP,
                "isOnlineOnly", false);
        JsonNode data = restClient.post(
                properties.apiBaseUrl(), "/msg/send_msg", tokenManager.getBotToken(), body);
        return data.path("clientMsgID").asString();
    }
}
