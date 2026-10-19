package com.financetracker.assistant.dto;

import com.financetracker.assistant.model.AssistantThread;

import java.time.Instant;
import java.util.UUID;

/** An assistant conversation (ADR-024). */
public record ThreadResponse(UUID id, String title, Instant createdAt) {

    public static ThreadResponse from(AssistantThread thread) {
        return new ThreadResponse(thread.getId(), thread.getTitle(), thread.getCreatedAt());
    }
}
