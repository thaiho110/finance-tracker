package com.financetracker.notifications.service;

import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.notifications.dto.NotificationResponse;
import com.financetracker.notifications.event.NotifyUserRequested;
import com.financetracker.notifications.model.Notification;
import com.financetracker.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The in-app notification subsystem (ADR-021). Persist every notification to the user's feed and track read
 * state; clients show a feed and an unread badge. This is platform and always on — there is no module to
 * switch it off. Delivery is in-app only: no browser/OS push and no device tokens.
 *
 * <p>Features create a notification either by calling {@link #notify} directly or by publishing a
 * {@link NotifyUserRequested} on the event bus (ADR-023), which keeps producers decoupled from this package.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationStream stream;

    /** Create a notification in the user's in-app feed and push it live to any open stream (ADR-021). */
    @Transactional
    public Notification notify(UUID userId, String type, String title, String body, String deepLink) {
        Notification saved = notifications.save(Notification.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .body(body)
            .deepLink(deepLink)
            .build());
        stream.send(userId, NotificationResponse.from(saved));
        return saved;
    }

    /** Bus bridge: any feature can publish a {@link NotifyUserRequested} instead of depending on this service. */
    @EventListener
    public void on(NotifyUserRequested event) {
        notify(event.userId(), event.type(), event.title(), event.body(), event.deepLink());
    }

    @Transactional(readOnly = true)
    public List<Notification> feed(UUID userId, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return unreadOnly
            ? notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
            : notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void markRead(UUID userId, UUID id) {
        Notification notification = notifications.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notifications.save(notification);
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notifications.markAllRead(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }
}
