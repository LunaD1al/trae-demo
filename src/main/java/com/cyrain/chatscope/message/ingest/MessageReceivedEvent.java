package com.cyrain.chatscope.message.ingest;

import java.time.OffsetDateTime;

/**
 * Payload of the {@code chatscope.message.received} Kafka event.
 *
 * <p>Keyed by {@code messageId} on the topic so downstream consumers (the Bot trigger worker) can
 * dedupe by message id and remain idempotent under redelivery.
 */
record MessageReceivedEvent(
        String messageId,
        String groupId,
        String openimUserId,
        OffsetDateTime sentAt,
        boolean isFromBot) {

    static MessageReceivedEvent from(IngestedMessage message) {
        return new MessageReceivedEvent(
                message.messageId(),
                message.groupId(),
                message.openimUserId(),
                message.sentAt(),
                message.fromBot());
    }
}
