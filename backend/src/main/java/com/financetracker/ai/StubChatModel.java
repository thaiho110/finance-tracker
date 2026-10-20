package com.financetracker.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Placeholder {@link ChatModel} that echoes a canned reply instead of calling a provider (ADR-024). It keeps the
 * assistant's shape correct — threads, messages, the tool registry and permission gating all work — while the
 * real Anthropic/Claude adapter (API key, native tool-use, streaming) is the next slice. A real adapter marked
 * {@code @Primary} replaces this bean.
 */
@Component
public class StubChatModel implements ChatModel {

    private static final Logger LOG = LoggerFactory.getLogger(StubChatModel.class);

    @Override
    public String complete(List<ChatTurn> history) {
        String latest = history.isEmpty() ? "" : history.get(history.size() - 1).content();
        LOG.info("assistant (stub): {} turn(s), latest=\"{}\"", history.size(), latest);
        return "The assistant isn't connected to a model yet. You said: " + latest;
    }
}
