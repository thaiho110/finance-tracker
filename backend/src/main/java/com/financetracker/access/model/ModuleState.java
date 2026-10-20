package com.financetracker.access.model;

/**
 * A module's state for one user (ADR-020 §4), as returned to clients so they can build navigation and the home
 * screen. {@code available} is whether the user's role and features grant any of the module's permissions;
 * {@code enabled} is the user's own on/off choice (default on). A module can be used only when both are true.
 *
 * @param code      the module name, e.g. {@code SPENDING}
 * @param enabled   the user's choice (default true)
 * @param available whether the user has any of the module's permissions to begin with
 */
public record ModuleState(String code, boolean enabled, boolean available) { }
