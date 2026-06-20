package com.cyrain.chatscope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Single fixed demo group settings.
 */
@ConfigurationProperties(prefix = "chatscope.demo")
public record DemoProperties(String groupId) {
}
