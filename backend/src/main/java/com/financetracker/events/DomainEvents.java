package com.financetracker.events;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Thin publisher over Spring's {@link ApplicationEventPublisher} (ADR-023 §1). Features publish a
 * {@link DomainEvent}; they never call the notification or webhook code directly. Listeners react with
 * {@code @EventListener}. Keeping one publisher type (rather than injecting the raw Spring publisher
 * everywhere) gives the bus a name and a single place to add cross-cutting behaviour later.
 */
@Component
@RequiredArgsConstructor
public class DomainEvents {

    private final ApplicationEventPublisher publisher;

    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
