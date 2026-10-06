package com.financetracker.common.config.rate_limiting;

/**
 * Abstraction for token-bucket rate limiting.
 * Implementations can be in-memory (Caffeine) or Redis (future).
 */
public interface RateLimiter {

    /**
     * Try to consume one token from the bucket identified by the given key.
     *
     * @param key      the bucket key (e.g., user ID, API key, or IP address)
     * @param clientId the X-Client-Id header value
     * @return true if the request is allowed, false if rate-limited
     */
    boolean tryConsume(String key, String clientId);

    /**
     * Returns the number of remaining tokens for the bucket.
     *
     * @param key      the bucket key
     * @param clientId the X-Client-Id header value
     * @return remaining tokens, or 0 if no bucket exists
     */
    long remainingTokens(String key, String clientId);

    /**
     * Returns the epoch-seconds when the bucket will be fully refilled.
     *
     * @param key      the bucket key
     * @param clientId the X-Client-Id header value
     * @return reset timestamp in epoch seconds, or 0 if unknown
     */
    long resetTimeSeconds(String key, String clientId);
}
