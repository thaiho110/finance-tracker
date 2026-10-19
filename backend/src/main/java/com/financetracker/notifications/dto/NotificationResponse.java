package com.financetracker.notifications.dto;

import com.financetracker.notifications.model.Notification;

import java.time.Instant;
import java.util.UUID;

/** One row of the in-app feed (ADR-021). */
public record NotificationResponse(
    UUID id,
    String type,
    String title,
    String body,
    String deepLink,
    boolean read,
    Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getTitle(),
            notification.getBody(),
            notification.getDeepLink(),
            notification.getReadAt() != null,
            notification.getCreatedAt());
    }
}
