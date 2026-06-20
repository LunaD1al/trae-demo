package com.cyrain.chatscope.message.ingest;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Idempotent persistence for normalized OpenIM group messages.
 */
@Repository
class RawMessageRepository {

    private final JdbcClient jdbcClient;

    RawMessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Insert the message unless {@code message_id} already exists.
     *
     * @return {@code true} if this call created the row, {@code false} if it was a duplicate callback
     */
    boolean insertIfAbsent(IngestedMessage message) {
        return jdbcClient.sql("""
                insert into raw_messages
                  (message_id, group_id, openim_user_id, sender_name, sent_at, content_text,
                   message_type, mentions_json, reply_to_message_id, is_from_bot,
                   visible_to_user_ids_json, raw_payload)
                values
                  (:messageId, :groupId, :openimUserId, :senderName, :sentAt, :contentText,
                   :messageType, cast(:mentions as jsonb), :replyTo, :fromBot,
                   cast(:visible as jsonb), cast(:rawPayload as jsonb))
                on conflict (message_id) do nothing
                returning id
                """)
                .param("messageId", message.messageId())
                .param("groupId", message.groupId())
                .param("openimUserId", message.openimUserId())
                .param("senderName", message.senderName())
                .param("sentAt", message.sentAt())
                .param("contentText", message.contentText())
                .param("messageType", message.messageType())
                .param("mentions", message.mentionsJson())
                .param("replyTo", message.replyToMessageId())
                .param("fromBot", message.fromBot())
                .param("visible", message.visibleToUserIdsJson())
                .param("rawPayload", message.rawPayloadJson())
                .query(Long.class)
                .optional()
                .isPresent();
    }
}
