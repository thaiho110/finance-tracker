package com.financetracker.access.model;

/**
 * Everything the application lets someone do, grouped by domain (ADR-018). The code checks these,
 * so the catalog lives in code; roles and per-user features in the database refer to them by name.
 * Renaming a constant needs a migration of {@code role_permissions} and {@code user_feature_overrides}.
 */
public enum Permission {
    TRANSACTION_READ(PermissionDomain.TRANSACTIONS, PermissionKind.ROLE, "See own transactions"),
    TRANSACTION_WRITE(PermissionDomain.TRANSACTIONS, PermissionKind.ROLE, "Save, edit and delete own transactions"),
    DASHBOARD_VIEW(PermissionDomain.DASHBOARD, PermissionKind.FEATURE, "Dashboard with totals and charts"),
    CSV_IMPORT(PermissionDomain.IMPORTS, PermissionKind.FEATURE, "Import bank statements from CSV"),
    RECEIPT_SCAN(PermissionDomain.IMPORTS, PermissionKind.FEATURE, "Scan receipts from a photo or PDF"),
    CATEGORY_READ(PermissionDomain.CATEGORIES, PermissionKind.ROLE, "See categories"),
    CATEGORY_WRITE(PermissionDomain.CATEGORIES, PermissionKind.ROLE, "Create own categories and keyword rules"),
    CATEGORY_ADMIN(PermissionDomain.CATEGORIES, PermissionKind.ROLE, "Manage the default categories and rules everyone shares"),
    USER_READ(PermissionDomain.ADMINISTRATION, PermissionKind.ROLE, "See accounts (never their transactions)"),
    USER_SUSPEND(PermissionDomain.ADMINISTRATION, PermissionKind.ROLE, "Disable and enable accounts with a lower role"),
    USER_CREATE(PermissionDomain.ADMINISTRATION, PermissionKind.ROLE, "Create accounts"),
    AUDIT_READ(PermissionDomain.ADMINISTRATION, PermissionKind.ROLE, "Read the audit log"),
    MONITORING_VIEW(PermissionDomain.MONITORING, PermissionKind.ROLE, "Open the monitoring dashboards"),
    ROLE_MANAGE(PermissionDomain.ADMINISTRATION, PermissionKind.OWNER, "Change roles and what each role may do"),
    FEATURE_MANAGE(PermissionDomain.ADMINISTRATION, PermissionKind.OWNER, "Switch features on or off for each user");

    private final PermissionDomain domain;
    private final PermissionKind kind;
    private final String description;

    Permission(PermissionDomain domain, PermissionKind kind, String description) {
        this.domain = domain;
        this.kind = kind;
        this.description = description;
    }

    public PermissionDomain domain() {
        return domain;
    }

    public PermissionKind kind() {
        return kind;
    }

    public String description() {
        return description;
    }
}
