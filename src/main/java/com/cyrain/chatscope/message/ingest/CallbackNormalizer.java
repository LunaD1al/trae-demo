package com.cyrain.chatscope.message.ingest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.cyrain.chatscope.config.BotProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

/**
 * Translates an OpenIM {@code callbackAfterSendGroupMsg} payload into the internal
 * {@link IngestedMessage}. This is the only place that understands OpenIM's wire format.
 */
@Component
class CallbackNormalizer {

    /** OpenIM {@code callbackCommand} for the after-send group message webhook. */
    static final String AFTER_SEND_GROUP_MSG = "callbackAfterSendGroupMsgCommand";

    private final BotProperties botProperties;
    private final ObjectMapper objectMapper;

    CallbackNormalizer(BotProperties botProperties, ObjectMapper objectMapper) {
        this.botProperties = botProperties;
        this.objectMapper = objectMapper;
    }

    IngestedMessage normalize(JsonNode payload) {
        String messageId = firstNonBlank(text(payload, "serverMsgID"), text(payload, "clientMsgID"));
        String groupId = text(payload, "groupID");
        String sendId = text(payload, "sendID");
        if (messageId == null || groupId == null || sendId == null) {
            throw new IllegalArgumentException("callback missing serverMsgID/groupID/sendID");
        }

        int contentType = payload.path("contentType").asInt(0);
        return new IngestedMessage(
                messageId,
                groupId,
                sendId,
                text(payload, "senderNickname"),
                resolveSentAt(payload),
                extractContentText(payload),
                String.valueOf(contentType),
                arrayJson(payload.path("atUserList")),
                null,
                sendId.equals(botProperties.openimUserId()),
                visibleToUserIds(payload),
                objectMapper.writeValueAsString(payload));
    }

    private OffsetDateTime resolveSentAt(JsonNode payload) {
        long epochMillis = payload.path("sendTime").asLong(0L);
        if (epochMillis <= 0) {
            epochMillis = payload.path("createTime").asLong(0L);
        }
        Instant instant = epochMillis > 0 ? Instant.ofEpochMilli(epochMillis) : Instant.now();
        return instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * The OpenIM {@code content} field is a JSON string whose shape depends on the message type.
     * Text uses {@code {"content":"..."}}, at-text uses {@code {"text":"..."}}. Fall back to the raw
     * string when it is not parseable JSON.
     */
    private String extractContentText(JsonNode payload) {
        String content = text(payload, "content");
        if (content == null) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(content);
            if (parsed.has("content")) {
                return parsed.path("content").asString();
            }
            if (parsed.has("text")) {
                return parsed.path("text").asString();
            }
            return content;
        } catch (RuntimeException ex) {
            return content;
        }
    }

    /** Read ChatScope visibility metadata from the message {@code ex} field, if present. */
    private String visibleToUserIds(JsonNode payload) {
        String ex = text(payload, "ex");
        if (ex == null) {
            return "[]";
        }
        try {
            JsonNode parsed = objectMapper.readTree(ex);
            JsonNode visible = parsed.path("chatscope").path("visible_to_user_ids");
            if (visible.isArray()) {
                return objectMapper.writeValueAsString(visible);
            }
        } catch (RuntimeException ignored) {
            // ex is free-form; ignore non-JSON or unexpected shapes.
        }
        return "[]";
    }

    private String arrayJson(JsonNode node) {
        return node.isArray() ? objectMapper.writeValueAsString(node) : "[]";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return (text == null || text.isBlank()) ? null : text;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null ? a : b;
    }
}
