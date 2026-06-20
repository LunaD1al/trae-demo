package com.cyrain.chatscope.message.ingest;

import java.util.concurrent.TimeUnit;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code chatscope.message.received} to Kafka.
 *
 * <p>The producer is configured idempotent and the record is keyed by {@code messageId}, so a given
 * message always lands on the same partition and downstream consumers can dedupe by key.
 */
@Component
class MessageReceivedPublisher {

    private static final Logger log = LoggerFactory.getLogger(MessageReceivedPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    MessageReceivedPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish the event synchronously so a send failure surfaces to the caller. The ingest service
     * only invokes this after a brand-new row was persisted, so it fires at most once per message.
     */
    void publish(MessageReceivedEvent event) {
        String payload = objectMapper.writeValueAsString(event);
        try {
            kafkaTemplate.send(MessageTopics.MESSAGE_RECEIVED, event.messageId(), payload)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Published {} for message_id={}", MessageTopics.MESSAGE_RECEIVED, event.messageId());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MessagePublishException(event.messageId(), ex);
        } catch (Exception ex) {
            throw new MessagePublishException(event.messageId(), ex);
        }
    }
}
