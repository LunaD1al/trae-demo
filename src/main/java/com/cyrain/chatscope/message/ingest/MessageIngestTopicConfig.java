package com.cyrain.chatscope.message.ingest;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topics owned by message ingest so {@code KafkaAdmin} creates them at startup
 * (the broker has auto-topic-creation disabled).
 */
@Configuration
class MessageIngestTopicConfig {

    @Bean
    NewTopic messageReceivedTopic() {
        return TopicBuilder.name(MessageTopics.MESSAGE_RECEIVED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
