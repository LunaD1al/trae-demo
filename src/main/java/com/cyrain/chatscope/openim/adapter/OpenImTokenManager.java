package com.cyrain.chatscope.openim.adapter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.cyrain.chatscope.config.BotProperties;
import tools.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Obtains and refreshes the OpenIM tokens ChatScope needs to call the REST API.
 *
 * <p>The admin token authenticates every server-side OpenIM call and is cached until shortly before
 * it expires. The bot token is what the bot uses to send Demo group messages; because the ChatScope
 * bot is provisioned as an OpenIM app-manager (notification) account it cannot mint its own user
 * token, so this manager transparently falls back to the admin token for bot sends.
 */
@Component
public class OpenImTokenManager {

    private static final Logger log = LoggerFactory.getLogger(OpenImTokenManager.class);

    /** Refresh the admin token this far before its reported expiry to avoid using a stale token. */
    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofMinutes(5);

    private final OpenImRestClient restClient;
    private final OpenImProperties properties;
    private final BotProperties botProperties;
    private final AtomicReference<CachedToken> adminToken = new AtomicReference<>();

    OpenImTokenManager(OpenImRestClient restClient, OpenImProperties properties, BotProperties botProperties) {
        this.restClient = restClient;
        this.properties = properties;
        this.botProperties = botProperties;
    }

    /** Cached OpenIM admin token, refreshed automatically as it nears expiry. */
    public String getAdminToken() {
        CachedToken current = adminToken.get();
        if (current != null && current.isFresh()) {
            return current.token();
        }
        synchronized (this) {
            current = adminToken.get();
            if (current != null && current.isFresh()) {
                return current.token();
            }
            CachedToken refreshed = requestAdminToken();
            adminToken.set(refreshed);
            return refreshed.token();
        }
    }

    /** Mint an OpenIM user token via {@code /auth/get_user_token}. */
    public String getUserToken(String userId, int platformId) {
        Map<String, Object> body = Map.of(
                "secret", properties.secret(),
                "userID", userId,
                "platformID", platformId);
        JsonNode data = restClient.post(properties.apiBaseUrl(), "/auth/get_user_token", getAdminToken(), body);
        return data.path("token").asString();
    }

    /**
     * Token the ChatScope bot uses to send Demo group messages. Falls back to the admin token when
     * the bot account cannot mint a user token (the expected case for app-manager bot accounts).
     */
    public String getBotToken() {
        try {
            return getUserToken(botProperties.openimUserId(), properties.botPlatformId());
        } catch (OpenImApiException ex) {
            log.debug("Bot user token unavailable (errCode={}), falling back to admin token", ex.errCode());
            return getAdminToken();
        }
    }

    private CachedToken requestAdminToken() {
        Map<String, Object> body = Map.of(
                "secret", properties.secret(),
                "userID", properties.adminUserId());
        JsonNode data = restClient.post(properties.apiBaseUrl(), "/auth/get_admin_token", null, body);
        String token = data.path("token").asString();
        long expireSeconds = data.path("expireTimeSeconds").asLong(0L);
        Instant expiresAt = expireSeconds > 0
                ? Instant.now().plusSeconds(expireSeconds)
                : Instant.now().plus(Duration.ofHours(1));
        log.info("Refreshed OpenIM admin token, expires at {}", expiresAt);
        return new CachedToken(token, expiresAt);
    }

    private record CachedToken(String token, Instant expiresAt) {

        boolean isFresh() {
            return Instant.now().isBefore(expiresAt.minus(EXPIRY_SAFETY_MARGIN));
        }
    }
}
