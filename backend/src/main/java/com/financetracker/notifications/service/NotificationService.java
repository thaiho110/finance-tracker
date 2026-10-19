package com.financetracker.notifications.service;

import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.notifications.event.NotifyUserRequested;
import com.financetracker.notifications.model.Notification;
import com.financetracker.notifications.model.PushToken;
import com.financetracker.notifications.model.PushTransport;
import com.financetracker.notifications.repository.NotificationRepository;
import com.financetracker.notifications.repository.PushTokenRepository;
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
 * The notification subsystem (ADR-021): persist every notification to the user's in-app feed, and push the
 * types that route to it. Features call {@link #notify} directly or publish a {@link NotifyUserRequested} on
 * the event bus (ADR-023). This is platform and always on — there is no module to switch it off.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final PushTokenRepository pushTokens;
    private final NotificationRouting routing;
    private final PushSender pushSender;

    /** Create a notification: always feed it, and push it if its type routes to push. */
    @Transactional
    public Notification notify(UUID userId, String type, String title, String body, String deepLink) {
        boolean push = routing.push(type);
        Notification saved = notifications.save(Notification.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .body(body)
            .deepLink(deepLink)
            .pushed(push)
            .build());
        if (push) {
            for (PushToken token : pushTokens.findByUserId(userId)) {
                pushSender.send(token, saved);
            }
        }
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

    /** Register or refresh this device's push token. A token moves to the current user if it was reused. */
    @Transactional
    public void registerToken(UUID userId, String client, PushTransport transport, String token) {
        PushToken existing = pushTokens.findByToken(token).orElse(null);
        if (existing == null) {
            pushTokens.save(PushToken.builder()
                .userId(userId)
                .client(client)
                .transport(transport)
                .token(token)
                .lastSeenAt(Instant.now())
                .build());
            return;
        }
        existing.setUserId(userId);
        existing.setClient(client);
        existing.setTransport(transport);
        existing.setLastSeenAt(Instant.now());
        pushTokens.save(existing);
    }

    /** Remove this device's token (on logout). Only the owner may remove their own token. */
    @Transactional
    public void removeToken(UUID userId, String token) {
        pushTokens.findByToken(token)
            .filter(existing -> existing.getUserId().equals(userId))
            .ifPresent(pushTokens::delete);
    }
}
