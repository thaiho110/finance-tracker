package com.financetracker.integrations.event;

import com.financetracker.events.DomainEvent;

import java.util.UUID;

/**
 * Something happened that outbound subscriptions may want (ADR-023). Published on the event bus; the webhook
 * dispatcher POSTs it to every active subscription of {@code userId} whose event types include {@code eventType}.
 * Distinct from notification events, so an in-app notification is never re-broadcast to the outside world.
 *
 * @param userId    whose subscriptions should receive it
 * @param eventType the event name, e.g. {@code integration.trigger}
 * @param payload   an opaque detail string (not echoed verbatim to subscribers in this cut)
 */
public record OutboundEvent(UUID userId, String eventType, String payload) implements DomainEvent { }
