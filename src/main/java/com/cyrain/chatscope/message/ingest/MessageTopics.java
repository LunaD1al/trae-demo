package com.cyrain.chatscope.message.ingest;

/**
 * Kafka topic names owned by the message ingest boundary.
 */
final class MessageTopics {

    /** Published after a non-bot Demo group message is idempotently persisted. */
    static final String MESSAGE_RECEIVED = "chatscope.message.received";

    private MessageTopics() {
    }
}
