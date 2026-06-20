package com.cyrain.chatscope.message.ingest;

import com.cyrain.chatscope.config.DemoProperties;
import tools.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Ingests OpenIM group-message callbacks.
 *
 * <p>Flow: normalize the payload, accept only the single configured Demo group, persist idempotently
 * by {@code message_id}, and publish {@code chatscope.message.received} exactly once — only when a new
 * row was created and the sender is not the bot. Bot messages are stored for context but never
 * republished, which prevents the bot from triggering itself. No model (DeepSeek) call happens here.
 */
@Service
public class MessageIngestService {

    private static final Logger log = LoggerFactory.getLogger(MessageIngestService.class);

    private final CallbackNormalizer normalizer;
    private final RawMessageRepository rawMessages;
    private final MessageReceivedPublisher publisher;
    private final DemoProperties demoProperties;

    MessageIngestService(
            CallbackNormalizer normalizer,
            RawMessageRepository rawMessages,
            MessageReceivedPublisher publisher,
            DemoProperties demoProperties) {
        this.normalizer = normalizer;
        this.rawMessages = rawMessages;
        this.publisher = publisher;
        this.demoProperties = demoProperties;
    }

    public IngestOutcome ingestGroupMessage(JsonNode payload) {
        IngestedMessage message = normalizer.normalize(payload);

        if (!demoProperties.groupId().equals(message.groupId())) {
            log.debug("Ignoring message for non-demo group {}", message.groupId());
            return IngestOutcome.IGNORED_NON_DEMO;
        }

        boolean inserted = rawMessages.insertIfAbsent(message);
        if (!inserted) {
            log.debug("Duplicate callback for message_id={}, skipping publish", message.messageId());
            return IngestOutcome.DUPLICATE;
        }

        if (message.fromBot()) {
            log.debug("Stored bot message_id={} without publishing", message.messageId());
            return IngestOutcome.STORED_BOT;
        }

        publisher.publish(MessageReceivedEvent.from(message));
        return IngestOutcome.STORED_PUBLISHED;
    }

    /** Result of an ingest attempt. */
    public enum IngestOutcome {
        STORED_PUBLISHED,
        STORED_BOT,
        DUPLICATE,
        IGNORED_NON_DEMO
    }
}
