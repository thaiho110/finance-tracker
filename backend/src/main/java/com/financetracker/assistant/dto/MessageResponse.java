package com.financetracker.assistant.dto;

import com.financetracker.assistant.model.AssistantMessage;
import com.financetracker.assistant.model.MessageRole;

import java.time.Instant;
import java.util.UUID;

/** One message in an assistant thread (ADR-024). */
public record MessageResponse(UUID id, MessageRole role, String content, Instant createdAt) {

    public static MessageResponse from(AssistantMessage message) {
        return new MessageResponse(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }
}
