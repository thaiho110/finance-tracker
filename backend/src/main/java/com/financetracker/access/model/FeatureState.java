package com.financetracker.access.model;

/**
 * Whether a feature is on for one user, and why.
 *
 * @param permission  the feature
 * @param roleDefault true when the user's role grants it
 * @param override    true/false when switched on/off for this user, null when following the role
 * @param enabled     the result: the override when there is one, otherwise the role default
 */
public record FeatureState(Permission permission, boolean roleDefault, Boolean override, boolean enabled) { }
