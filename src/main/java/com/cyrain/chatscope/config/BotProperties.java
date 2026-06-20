package com.cyrain.chatscope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ChatScope bot identity inside OpenIM.
 */
@ConfigurationProperties(prefix = "chatscope.bot")
public record BotProperties(String openimUserId) {
}
