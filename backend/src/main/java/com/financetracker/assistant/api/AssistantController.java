package com.financetracker.assistant.api;

import com.financetracker.assistant.dto.CreateThreadRequest;
import com.financetracker.assistant.dto.MessageResponse;
import com.financetracker.assistant.dto.PostMessageRequest;
import com.financetracker.assistant.dto.ThreadResponse;
import com.financetracker.assistant.service.AssistantService;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The AI assistant (ADR-024). In the ASSISTANT module; every endpoint needs {@code ASSISTANT_USE} (enforced by
 * URL in SecurityConfig) and is user-scoped.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@Tag(name = "Assistant", description = "Conversational AI assistant threads and messages")
public class AssistantController {

    private final AssistantService assistant;

    @GetMapping("/threads")
    @Operation(summary = "List the caller's assistant threads, newest first")
    public ResponseEntity<List<ThreadResponse>> threads(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(assistant.listThreads(user.getId()).stream().map(ThreadResponse::from).toList());
    }

    @PostMapping("/threads")
    @Operation(summary = "Start an assistant thread")
    public ResponseEntity<ThreadResponse> create(@Valid @RequestBody CreateThreadRequest request,
                                                 @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(ThreadResponse.from(assistant.createThread(user.getId(), request.title())));
    }

    @GetMapping("/threads/{id}/messages")
    @Operation(summary = "The messages in one thread, oldest first")
    public ResponseEntity<List<MessageResponse>> messages(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(assistant.listMessages(user.getId(), id).stream().map(MessageResponse::from).toList());
    }

    @PostMapping("/threads/{id}/messages")
    @Operation(summary = "Send a message and get the assistant's reply")
    public ResponseEntity<MessageResponse> post(@PathVariable UUID id,
                                                @Valid @RequestBody PostMessageRequest request,
                                                @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(MessageResponse.from(assistant.postMessage(user.getId(), id, request.content())));
    }
}
