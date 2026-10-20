package com.financetracker.assistant.service;

import com.financetracker.ai.ChatModel;
import com.financetracker.assistant.model.AssistantMessage;
import com.financetracker.assistant.model.AssistantThread;
import com.financetracker.assistant.model.MessageRole;
import com.financetracker.assistant.repository.AssistantMessageRepository;
import com.financetracker.assistant.repository.AssistantThreadRepository;
import com.financetracker.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssistantService Unit Tests")
class AssistantServiceTest {

    @Mock private AssistantThreadRepository threads;
    @Mock private AssistantMessageRepository messages;
    @Mock private ChatModel chatModel;

    private AssistantService assistant;

    private final UUID userId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assistant = new AssistantService(threads, messages, chatModel);
    }

    @Test
    @DisplayName("Posting a message records the user turn, asks the model, and records the reply")
    void postMessageRecordsBoth() {
        when(threads.findByIdAndUserId(threadId, userId))
            .thenReturn(Optional.of(AssistantThread.builder().id(threadId).userId(userId).build()));
        when(messages.findByThreadIdOrderByCreatedAtAsc(threadId)).thenReturn(List.of());
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatModel.complete(any())).thenReturn("the reply");

        AssistantMessage reply = assistant.postMessage(userId, threadId, "hello");

        assertThat(reply.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(reply.getContent()).isEqualTo("the reply");
        verify(messages, times(2)).save(any());
        verify(chatModel).complete(any());
    }

    @Test
    @DisplayName("Acting on a thread the caller doesn't own is a not-found")
    void unknownThreadRejected() {
        when(threads.findByIdAndUserId(threadId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assistant.postMessage(userId, threadId, "hi"))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
