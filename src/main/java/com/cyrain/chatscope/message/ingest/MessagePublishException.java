package com.cyrain.chatscope.message.ingest;

/**
 * Raised when publishing {@code chatscope.message.received} to Kafka fails.
 */
class MessagePublishException extends RuntimeException {

    MessagePublishException(String messageId, Throwable cause) {
        super("Failed to publish message.received for message_id=" + messageId, cause);
    }
}
