package com.cyrain.chatscope.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Daily AI request quota settings.
 */
@ConfigurationProperties(prefix = "chatscope.ai")
public record AiProperties(int defaultDailyLimit, String quotaTimezone) {

    public ZoneId quotaZoneId() {
        return ZoneId.of(quotaTimezone);
    }
}
