package com.financetracker.assistant.tool;

import com.financetracker.access.model.Permission;

/**
 * A capability the assistant can invoke on the user's behalf (ADR-024 §2). Each feature contributes its own
 * tools; a tool is offered to the model only when the user holds {@link #requiredPermission()}, so the assistant
 * can never do what the user could not. Execution of a mutating tool goes through a user-confirmed step
 * (the confirm-card), which is a follow-up to this scaffold.
 */
public interface AssistantTool {

    /** The tool's name, as exposed to the model. */
    String name();

    /** The permission the caller must hold for this tool to be offered. */
    Permission requiredPermission();
}
