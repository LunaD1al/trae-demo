package com.cyrain.chatscope.openim.adapter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenIM REST endpoints and the server-side credentials ChatScope uses to call them.
 *
 * <p>The secret and admin user id authenticate the backend against the OpenIM API server. They are
 * internal-only and must never be exposed to the public Demo client.
 */
@ConfigurationProperties(prefix = "chatscope.openim")
public record OpenImProperties(
        String apiBaseUrl,
        String chatApiBaseUrl,
        String adminApiBaseUrl,
        String secret,
        String adminUserId,
        int botPlatformId,
        boolean allowPublicDirectAccess,
        String callbackSecret) {
}
