package com.cyrain.chatscope.health;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("kafka")
class KafkaHealthIndicator implements HealthIndicator {

    private static final long TIMEOUT_SECONDS = 3;

    private final String bootstrapServers;
    private final String clientId;

    KafkaHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers:}") String bootstrapServers,
            @Value("${spring.kafka.client-id:chatscope-backend-health}") String clientId) {
        this.bootstrapServers = bootstrapServers;
        this.clientId = clientId;
    }

    @Override
    public Health health() {
        if (!StringUtils.hasText(bootstrapServers)) {
            return Health.down()
                    .withDetail("reason", "spring.kafka.bootstrap-servers is empty")
                    .build();
        }

        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(AdminClientConfig.CLIENT_ID_CONFIG, clientId);
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) Duration.ofSeconds(TIMEOUT_SECONDS).toMillis());
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) Duration.ofSeconds(TIMEOUT_SECONDS).toMillis());

        try (AdminClient adminClient = AdminClient.create(properties)) {
            DescribeClusterResult cluster = adminClient.describeCluster();
            Map<String, Object> details = new HashMap<>();
            details.put("bootstrapServers", bootstrapServers);
            details.put("clusterId", cluster.clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            details.put("nodeCount", cluster.nodes().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size());
            return Health.up().withDetails(details).build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("bootstrapServers", bootstrapServers)
                    .build();
        }
    }
}
