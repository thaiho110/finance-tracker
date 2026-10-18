import { useSelector } from 'react-redux';
import { can, type Permission } from '@finance-tracker/shared';
import type { RootState } from '@/store';

export { can, type Permission };

/** `const can = useCan(); can('CSV_IMPORT')` */
export function useCan(): (permission: Permission) => boolean {
  const permissions = useSelector((state: RootState) => state.auth.permissions);
  return (permission) => can(permissions, permission);
}
