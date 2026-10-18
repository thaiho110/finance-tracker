import { Navigate } from 'react-router-dom';
import { Lock } from 'lucide-react';
import type { ReactNode } from 'react';
import { useCan, type Permission } from '@/lib/permissions';

/** Shown in place of a page or section the user's role or features don't include. */
export function NotAvailable({ title = 'Not available on your account', detail }: { title?: string; detail?: string }) {
  return (
    <div className="mx-auto mt-16 flex max-w-md flex-col items-center gap-3 rounded-[12px] border border-border-default bg-bg-surface p-8 text-center">
      <div className="flex size-10 items-center justify-center rounded-full bg-bg-muted text-text-muted">
        <Lock className="size-5" />
      </div>
      <p className="text-base font-semibold text-text-default">{title}</p>
      <p className="text-sm text-text-muted">{detail ?? 'Ask an administrator if you need it switched on.'}</p>
    </div>
  );
}

/** Renders the page only when the user has the permission. */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const can = useCan();
  return can(permission) ? <>{children}</> : <NotAvailable />;
}

/** Sends "/" to the first page this user can open. */
export function HomeRedirect() {
  const can = useCan();
  const target = can('DASHBOARD_VIEW')
    ? '/dashboard'
    : can('TRANSACTION_READ')
      ? '/transactions'
      : can('USER_READ')
        ? '/admin/users'
        : '/dashboard';
  return <Navigate to={target} replace />;
}
