package com.financetracker.assistant.service;

import com.financetracker.ai.ChatModel;
import com.financetracker.ai.ChatTurn;
import com.financetracker.assistant.model.AssistantMessage;
import com.financetracker.assistant.model.AssistantThread;
import com.financetracker.assistant.model.MessageRole;
import com.financetracker.assistant.repository.AssistantMessageRepository;
import com.financetracker.assistant.repository.AssistantThreadRepository;
import com.financetracker.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The AI assistant (ADR-024): per-user conversation threads whose messages are routed to a {@link ChatModel}.
 * In the ASSISTANT module ({@code ASSISTANT_USE}). Tool-calling, confirm-cards and streaming are follow-ups to
 * this scaffold; today the model reply is persisted and returned directly.
 */
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final AssistantThreadRepository threads;
    private final AssistantMessageRepository messages;
    private final ChatModel chatModel;

    @Transactional
    public AssistantThread createThread(UUID userId, String title) {
        return threads.save(AssistantThread.builder().userId(userId).title(title).build());
    }

    @Transactional(readOnly = true)
    public List<AssistantThread> listThreads(UUID userId) {
        return threads.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<AssistantMessage> listMessages(UUID userId, UUID threadId) {
        requireThread(userId, threadId);
        return messages.findByThreadIdOrderByCreatedAtAsc(threadId);
    }

    /** Record the user's message, ask the model, and record and return its reply. */
    @Transactional
    public AssistantMessage postMessage(UUID userId, UUID threadId, String content) {
        requireThread(userId, threadId);
        messages.save(AssistantMessage.builder()
            .threadId(threadId).role(MessageRole.USER).content(content).build());
        List<ChatTurn> history = messages.findByThreadIdOrderByCreatedAtAsc(threadId).stream()
            .map(message -> new ChatTurn(message.getRole() == MessageRole.USER ? "user" : "assistant", message.getContent()))
            .toList();
        String reply = chatModel.complete(history);
        return messages.save(AssistantMessage.builder()
            .threadId(threadId).role(MessageRole.ASSISTANT).content(reply).build());
    }

    private AssistantThread requireThread(UUID userId, UUID threadId) {
        return threads.findByIdAndUserId(threadId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Thread not found"));
    }
}
