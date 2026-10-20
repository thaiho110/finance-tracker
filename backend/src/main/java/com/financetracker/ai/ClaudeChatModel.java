package com.financetracker.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The real {@link ChatModel} (ADR-024 §1): talks to Anthropic via the official Java SDK. It is {@code @Primary},
 * so the assistant uses it whenever an API key is configured; with no key it falls back to {@link StubChatModel}
 * so the app still runs. The key comes from {@code anthropic.api-key} / the {@code ANTHROPIC_API_KEY} env var
 * (Spring relaxed binding), and the model defaults to {@code claude-opus-5-5}.
 *
 * <p>Follow-ups (ADR-024): tool-calling + confirm-cards, streaming (SSE), and server-side refusal fallbacks.
 */
@Component
@Primary
public class ClaudeChatModel implements ChatModel {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudeChatModel.class);
    private static final long MAX_TOKENS = 16000L;
    private static final String SYSTEM = "You are Kept's assistant. Help the user with their money, household and the "
        + "app, concisely and in their language. You can only do what the user can do themselves; never claim to "
        + "have taken an action you cannot perform.";

    private final StubChatModel fallback;
    private final String apiKey;
    private final String model;
    private volatile AnthropicClient client;

    public ClaudeChatModel(StubChatModel fallback,
                           @Value("${anthropic.api-key:}") String apiKey,
                           @Value("${anthropic.model:claude-opus-5-5}") String model) {
        this.fallback = fallback;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(List<ChatTurn> history) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallback.complete(history);
        }
        try {
            MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM);
            for (ChatTurn turn : history) {
                if ("assistant".equals(turn.role())) {
                    params.addAssistantMessage(turn.content());
                } else {
                    params.addUserMessage(turn.content());
                }
            }
            Message response = client().messages().create(params.build());
            StringBuilder out = new StringBuilder();
            response.content().forEach(block -> block.text().ifPresent(text -> out.append(text.text())));
            return out.isEmpty() ? "(The assistant returned no text.)" : out.toString();
        } catch (RuntimeException e) {
            LOG.error("Assistant model call failed", e);
            return "The assistant is temporarily unavailable. Please try again.";
        }
    }

    private AnthropicClient client() {
        AnthropicClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                    client = current;
                }
            }
        }
        return current;
    }
}
