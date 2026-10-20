package com.financetracker.access.model;

import java.util.Locale;
import java.util.Set;

/**
 * A user-facing part of Kept that a user can turn on or off for themselves (ADR-020 §4). A module groups the
 * {@link Permission}s it covers; turning it off subtracts those permissions from what the user can do (it never
 * adds any — see {@code AccessService.effectivePermissions}). Administration and monitoring permissions belong
 * to no module and are never affected.
 *
 * <p>Only modules whose permissions already exist are listed. Tabs and Statements (ADR-020 §5/§6) join when
 * those features are built.
 */
public enum Module {

    SPENDING(Set.of(
        Permission.TRANSACTION_READ,
        Permission.TRANSACTION_WRITE,
        Permission.DASHBOARD_VIEW,
        Permission.CSV_IMPORT,
        Permission.RECEIPT_SCAN,
        Permission.CATEGORY_READ,
        Permission.CATEGORY_WRITE,
        Permission.BUDGETS)),

    ASSISTANT(Set.of(Permission.ASSISTANT_USE)),

    INTEGRATIONS(Set.of(Permission.INTEGRATIONS_MANAGE)),

    MEALS(Set.of(Permission.MEALS_CALL, Permission.MEALS_RESPOND));

    private final Set<Permission> permissions;

    Module(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    /** @throws IllegalArgumentException for an unknown module code (→ 400) */
    public static Module from(String code) {
        try {
            return Module.valueOf(code == null ? "" : code.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown module: " + code, e);
        }
    }
}
