package com.cyrain.chatscope.message.ingest;

import java.time.OffsetDateTime;

/**
 * Standardized internal representation of an OpenIM group message.
 *
 * <p>Produced by the callback normalizer so no other component touches the raw OpenIM payload. The
 * full original payload is retained as {@code rawPayloadJson} for audit and replay.
 */
record IngestedMessage(
        String messageId,
        String groupId,
        String openimUserId,
        String senderName,
        OffsetDateTime sentAt,
        String contentText,
        String messageType,
        String mentionsJson,
        String replyToMessageId,
        boolean fromBot,
        String visibleToUserIdsJson,
        String rawPayloadJson) {
}
