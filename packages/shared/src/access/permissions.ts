/** Permission codes from the backend catalog (ADR-018). The server enforces them; clients only hide what can't be used. */
export type Permission =
  | 'TRANSACTION_READ'
  | 'TRANSACTION_WRITE'
  | 'DASHBOARD_VIEW'
  | 'CSV_IMPORT'
  | 'RECEIPT_SCAN'
  | 'CATEGORY_READ'
  | 'CATEGORY_WRITE'
  | 'CATEGORY_ADMIN'
  | 'BUDGETS'
  | 'ASSISTANT_USE'
  | 'INTEGRATIONS_MANAGE'
  | 'MEALS_CALL'
  | 'MEALS_RESPOND'
  | 'USER_READ'
  | 'USER_SUSPEND'
  | 'USER_CREATE'
  | 'AUDIT_READ'
  | 'MONITORING_VIEW'
  | 'ROLE_MANAGE'
  | 'FEATURE_MANAGE';

/**
 * Whether these permissions include `permission`. `null` means not known yet (e.g. offline on
 * startup): allow, and let the server decide.
 */
export function can(permissions: readonly string[] | null, permission: Permission): boolean {
  return permissions === null || permissions.includes(permission);
}
