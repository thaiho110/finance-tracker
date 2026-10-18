package com.financetracker.access.model;

/** How a {@link Permission} can be granted (ADR-018). */
public enum PermissionKind {
    /** Granted only through a role. */
    ROLE,
    /** Granted through a role and switchable on or off per user; future paid features use this kind. */
    FEATURE,
    /** Super admin only; can't be put in any role. */
    OWNER
}
