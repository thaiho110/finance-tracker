package com.financetracker.assistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * One message in an assistant thread (ADR-024 §3). A mutating action the assistant proposes rides here as
 * structured {@code toolCalls} (a confirm-card the user approves); stored as JSON text, unused by the stub model.
 */
@Entity
@Table(name = "assistant_messages")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "tool_calls", columnDefinition = "text")
    private String toolCalls;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
