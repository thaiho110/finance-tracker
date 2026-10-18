import { useSelector } from 'react-redux';
import type { RootState } from '@/store';

/** Permission codes from the backend catalog (ADR-018). The server enforces them; the UI only hides what you can't use. */
export type Permission =
  | 'TRANSACTION_READ'
  | 'TRANSACTION_WRITE'
  | 'DASHBOARD_VIEW'
  | 'CSV_IMPORT'
  | 'RECEIPT_SCAN'
  | 'CATEGORY_READ'
  | 'USER_READ'
  | 'USER_SUSPEND'
  | 'USER_CREATE'
  | 'AUDIT_READ'
  | 'MONITORING_VIEW'
  | 'ROLE_MANAGE'
  | 'FEATURE_MANAGE';

/**
 * Whether the signed-in user may do this. `null` means the permissions aren't known yet (e.g. the
 * server was unreachable on startup): show the UI and let the server decide.
 */
export function can(permissions: string[] | null, permission: Permission): boolean {
  return permissions === null || permissions.includes(permission);
}

/** `const can = useCan(); can('CSV_IMPORT')` */
export function useCan(): (permission: Permission) => boolean {
  const permissions = useSelector((state: RootState) => state.auth.permissions);
  return (permission) => can(permissions, permission);
}
