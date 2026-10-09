package com.financetracker.common.config.rate_limiting;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for rate limiting.
 * Supports per-client overrides with a default fallback.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.rate-limiting")
public class RateLimitProperties {

    /** Which RateLimiter implementation to use: "in-memory" or "redis" */
    private String type = "in-memory";

    /** Default limits applied when no client-specific config exists */
    private ClientLimit defaults = new ClientLimit(100, 100, Duration.ofMinutes(1));

    /** Per-client overrides keyed by X-Client-Id prefix */
    private Map<String, ClientLimit> clients = new HashMap<>();

    public Map<String, ClientLimit> getClients() {
        return Collections.unmodifiableMap(clients);
    }

    public void setClients(Map<String, ClientLimit> clients) {
        this.clients = new HashMap<>(clients);
    }

    public ClientLimit getDefaults() {
        return defaults;
    }

    public void setDefaults(ClientLimit defaults) {
        this.defaults = defaults;
    }

    @Getter
    @Setter
    public static class ClientLimit {
        @Positive
        private int capacity;

        @Positive
        private int refillTokens;

        private Duration refillPeriod;

        public ClientLimit() {
            this(100, 100, Duration.ofMinutes(1));
        }

        public ClientLimit(int capacity, int refillTokens, Duration refillPeriod) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriod = refillPeriod;
        }
    }
}
