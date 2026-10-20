package com.financetracker.ai;

/**
 * One turn of a conversation passed to a {@link ChatModel} (ADR-024). {@code role} is {@code user} or
 * {@code assistant}; provider-specific encoding is the adapter's concern.
 */
public record ChatTurn(String role, String content) { }
