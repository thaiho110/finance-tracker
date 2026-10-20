package com.financetracker.ai;

import java.util.List;

/**
 * The provider-agnostic chat model port (ADR-024 §1). The first real implementation targets Anthropic/Claude;
 * a local model is a future implementation of the same interface, so features depend on this, never on a vendor.
 */
public interface ChatModel {

    /** The assistant's reply given the prior turns (oldest first) ending with the user's latest message. */
    String complete(List<ChatTurn> history);
}
