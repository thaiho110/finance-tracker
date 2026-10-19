package com.financetracker.notifications.event;

import com.financetracker.events.DomainEvent;

import java.util.UUID;

/**
 * A feature asks the notification subsystem to notify a user (ADR-021 §1). Publishing this on the event bus
 * (ADR-023) keeps producers decoupled from delivery: the subsystem decides channels from the {@code type}
 * and persists + (optionally) pushes. Producers may also call {@code NotificationService.notify} directly.
 *
 * @param userId   who to notify
 * @param type     the producing feature's type, e.g. {@code meal.call}
 * @param title    short headline
 * @param body     optional detail
 * @param deepLink optional client route to open
 */
public record NotifyUserRequested(UUID userId, String type, String title, String body, String deepLink)
    implements DomainEvent { }
