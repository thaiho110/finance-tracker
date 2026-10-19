package com.financetracker.notifications.service;

import com.financetracker.notifications.model.Notification;
import com.financetracker.notifications.model.PushToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder {@link PushSender} that logs instead of sending (ADR-021). It keeps the subsystem's shape
 * correct — the feed, token registration and push decision all work — while the real Web Push (VAPID)
 * adapter and its client service worker are the next slice. A real adapter replaces this bean.
 */
@Component
public class LoggingPushSender implements PushSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void send(PushToken token, Notification notification) {
        LOG.info("push (stub, not yet wired to a transport): user={} client={} transport={} type={} title=\"{}\"",
            token.getUserId(), token.getClient(), token.getTransport(), notification.getType(), notification.getTitle());
    }
}
