package com.financetracker.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live delivery of notifications over Server-Sent Events (ADR-021). Clients open one stream per browser; when a
 * feature creates a notification for a user, every open stream of that user receives it in real time (the basis
 * for a toast + sound on other people's screens). In-process only — fine for a single-node modular monolith.
 */
@Component
public class NotificationStream {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationStream.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Open a stream for a user; it is removed on completion, timeout or error. */
    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /** Push a notification payload to every open stream of a user. */
    public void send(UUID userId, Object payload) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (IOException | RuntimeException e) {
                LOG.debug("Dropping dead notification stream for {}", userId);
                remove(userId, emitter);
            }
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
