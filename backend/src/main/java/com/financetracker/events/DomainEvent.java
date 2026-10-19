package com.financetracker.events;

import java.util.UUID;

/**
 * The internal event bus (ADR-023 §1). A {@code DomainEvent} is published in-process on Spring's
 * {@code ApplicationEventPublisher} and carries the user it concerns, so listeners (notifications,
 * outbound webhooks) can fan out to the right recipient. This is deliberately <em>not</em> a message
 * broker: Kept is one process (ADR-001), so in-process dispatch is enough until it is ever split.
 */
public interface DomainEvent {

    /** The user this event concerns. */
    UUID userId();
}
