package com.financetracker.assistant.tool;

import com.financetracker.access.model.Permission;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Collects the {@link AssistantTool}s features contribute and filters them by the caller's permissions
 * (ADR-024 §2). Empty until features register tools; it is the single place the permission boundary is applied,
 * so the assistant's power can never exceed the user's.
 */
@Component
public class ToolRegistry {

    private final List<AssistantTool> tools;

    public ToolRegistry(List<AssistantTool> tools) {
        this.tools = List.copyOf(tools);
    }

    /** The tools the caller may use, given their effective permissions. */
    public List<AssistantTool> availableTo(Set<Permission> permissions) {
        return tools.stream().filter(tool -> permissions.contains(tool.requiredPermission())).toList();
    }
}
