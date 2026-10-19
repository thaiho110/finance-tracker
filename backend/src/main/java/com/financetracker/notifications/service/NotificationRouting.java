package com.financetracker.notifications.service;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Per-type channel routing (ADR-021 §2). Everything is written to the in-app feed; this decides which types
 * are <em>also</em> delivered to push. Routing lives with the subsystem, not scattered at call sites, so a
 * noisy type can be quieted in one place.
 */
@Component
public class NotificationRouting {

    /** Types delivered to push in addition to the in-app feed. */
    private static final Set<String> PUSH_TYPES = Set.of(
        "meal.call"          // ADR-025: a household meal call must reach devices
    );

    public boolean push(String type) {
        return PUSH_TYPES.contains(type);
    }
}
