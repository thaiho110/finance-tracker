package com.financetracker.notifications.service;

import com.financetracker.notifications.model.Notification;
import com.financetracker.notifications.model.PushToken;

/**
 * Port for delivering a notification to one device (ADR-021 §3). Web Push (VAPID), FCM and APNs are each an
 * adapter behind this interface; the subsystem calls {@code send} per registered token and does not care how.
 */
public interface PushSender {

    void send(PushToken token, Notification notification);
}
