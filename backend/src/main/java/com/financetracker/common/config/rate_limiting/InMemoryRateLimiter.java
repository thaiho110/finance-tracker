package com.financetracker.common.config.rate_limiting;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * In-memory token-bucket rate limiter backed by a Caffeine cache.
 * Each unique key (user, API key, or IP) gets its own Bucket4j bucket.
 */
@Component
@ConditionalOnProperty(name = "app.rate-limiting.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

    private final Cache<String, Bucket> bucketCache;
    private final RateLimitProperties properties;

    public InMemoryRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
        this.bucketCache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();
    }

    @Override
    public boolean tryConsume(String key, String clientId) {
        Bucket bucket = bucketCache.get(bucketKey(key, clientId), k -> createBucket(clientId));
        return bucket.tryConsume(1);
    }

    @Override
    public long remainingTokens(String key, String clientId) {
        Bucket bucket = bucketCache.getIfPresent(bucketKey(key, clientId));
        return bucket != null ? bucket.getAvailableTokens() : capacityFor(clientId);
    }

    @Override
    public long resetTimeSeconds(String key, String clientId) {
        Bucket bucket = bucketCache.getIfPresent(bucketKey(key, clientId));
        if (bucket == null) {
            return 0;
        }
        // Estimate: tokens / refill rate gives approximate seconds to full
        long tokens = bucket.getAvailableTokens();
        long capacity = capacityFor(clientId);
        if (tokens >= capacity) {
            return 0;
        }
        RateLimitProperties.ClientLimit limit = resolveLimit(clientId);
        long nanosPerToken = limit.getRefillPeriod().toNanos() / limit.getRefillTokens();
        return System.currentTimeMillis() / 1000 + ((capacity - tokens) * nanosPerToken) / 1_000_000_000;
    }

    private Bucket createBucket(String clientId) {
        RateLimitProperties.ClientLimit limit = resolveLimit(clientId);
        Bandwidth bandwidth = Bandwidth.classic(limit.getCapacity(),
            Refill.greedy(limit.getRefillTokens(), limit.getRefillPeriod()));
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private RateLimitProperties.ClientLimit resolveLimit(String clientId) {
        // Match by prefix: "finance-tracker-device-abc" matches "finance-tracker-device"
        return properties.getClients().entrySet().stream()
            .filter(e -> clientId != null && clientId.startsWith(e.getKey()))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElse(properties.getDefaults());
    }

    private long capacityFor(String clientId) {
        return resolveLimit(clientId).getCapacity();
    }

    private static String bucketKey(String key, String clientId) {
        return clientId + ":" + key;
    }
}
