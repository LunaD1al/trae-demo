package com.cyrain.chatscope.abuse;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counters backed by Redis.
 *
 * <p>Each counter key covers one (dimension, window) pair. The first increment in a window sets the
 * key TTL so the window expires automatically. This is deliberately simple rather than a strict
 * token bucket: it is sufficient to cap script-driven abuse on the public Demo entry points.
 */
@Component
public class RateLimiter {

    private static final String KEY_PREFIX = "chatscope:abuse:";

    private final StringRedisTemplate redis;

    RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Increment the window counter and report whether it is still within {@code limit}.
     *
     * @return {@code true} if the post-increment count is {@code <= limit} (request allowed)
     */
    public boolean incrementAndCheck(String dimension, int limit, Duration window) {
        long count = increment(dimension, window);
        return count <= limit;
    }

    /** Whether the current window counter is already at or above {@code limit}, without incrementing. */
    public boolean isAtOrOverLimit(String dimension, int limit) {
        String value = redis.opsForValue().get(KEY_PREFIX + dimension);
        long current = value == null ? 0L : Long.parseLong(value);
        return current >= limit;
    }

    /** Increment a counter (used to consume budget only after a successful send). */
    public long increment(String dimension, Duration window) {
        String key = KEY_PREFIX + dimension;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
        return count == null ? 0L : count;
    }

    /** Set a short-lived marker (e.g. resend cooldown) if not already present. */
    public void setMarker(String dimension, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + dimension, "1", ttl);
    }

    /** Whether a marker key currently exists. */
    public boolean markerExists(String dimension) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + dimension));
    }
}
