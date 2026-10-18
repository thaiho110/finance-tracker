package com.financetracker.admin.model;

/** Administrative changes recorded in the audit log. */
public enum AuditAction {
    USER_CREATED,
    USER_ENABLED,
    USER_DISABLED,
    ROLE_CHANGED,
    FEATURE_CHANGED,
    ROLE_PERMISSIONS_CHANGED,
    SUPER_ADMIN_BOOTSTRAPPED
}
