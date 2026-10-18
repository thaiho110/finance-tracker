package com.financetracker.admin.model;

/** Administrative changes recorded in the audit log. */
public enum AuditAction {
    USER_CREATED,
    USER_ENABLED,
    USER_DISABLED,
    ROLE_CHANGED,
    FEATURE_CHANGED,
    ROLE_PERMISSIONS_CHANGED,
    SUPER_ADMIN_BOOTSTRAPPED,
    SYSTEM_CATEGORY_CREATED,
    SYSTEM_CATEGORY_CHANGED,
    SYSTEM_RULE_SAVED,
    SYSTEM_RULE_DELETED
}
