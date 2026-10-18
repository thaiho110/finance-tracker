import { useMemo, useState } from 'react';
import { Check, Info, Lock } from 'lucide-react';
import { toast } from 'sonner';
import {
  errorMessage,
  humanize,
  useGetPermissionCatalogQuery,
  useGetRolesQuery,
  useUpdateRolePermissionsMutation,
  type PermissionInfo,
  type RoleInfo,
} from '@/store/api/admin-api';
import { useCan } from '@/lib/permissions';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from 'cn';

const DOMAIN_ORDER = ['TRANSACTIONS', 'DASHBOARD', 'IMPORTS', 'CATEGORIES', 'BUDGETS', 'ADMINISTRATION', 'MONITORING'];

function KindTag({ kind }: { kind: PermissionInfo['kind'] }) {
  if (kind === 'FEATURE') {
    return <Badge variant="outline" title="Can also be switched on or off for one user">Per-user feature</Badge>;
  }
  if (kind === 'OWNER') {
    return <Badge variant="outline" title="Only the super admin can hold this"><Lock className="size-3" />Super admin only</Badge>;
  }
  return null;
}

/**
 * The role table (ADR-018): which role may do what, per domain. Everyone with USER_READ can look;
 * only ROLE_MANAGE (the super admin) can change it.
 */
export default function AdminRolesPage() {
  const can = useCan();
  const editable = can('ROLE_MANAGE');
  const { data: roles = [], isLoading: rolesLoading } = useGetRolesQuery();
  const { data: catalog = [], isLoading: catalogLoading } = useGetPermissionCatalogQuery();
  const [updateRole, { isLoading: saving }] = useUpdateRolePermissionsMutation();
  /** Unsaved edits: role code → the full permission set it should have. */
  const [draft, setDraft] = useState<Record<string, Set<string>>>({});

  const groups = useMemo(() => {
    const byDomain = new Map<string, PermissionInfo[]>();
    for (const permission of catalog) {
      byDomain.set(permission.domain, [...(byDomain.get(permission.domain) ?? []), permission]);
    }
    return [...byDomain.entries()].sort(([a], [b]) => DOMAIN_ORDER.indexOf(a) - DOMAIN_ORDER.indexOf(b));
  }, [catalog]);

  const granted = (role: RoleInfo) => draft[role.code] ?? new Set(role.permissions);
  const dirtyRoles = roles.filter((role) => draft[role.code]);

  const toggle = (role: RoleInfo, permission: string) => {
    const next = new Set(granted(role));
    if (next.has(permission)) next.delete(permission); else next.add(permission);
    const unchanged = next.size === role.permissions.length && role.permissions.every((p) => next.has(p));
    setDraft((current) => {
      const copy = { ...current };
      if (unchanged) delete copy[role.code]; else copy[role.code] = next;
      return copy;
    });
  };

  const save = async () => {
    try {
      for (const role of dirtyRoles) {
        await updateRole({ code: role.code, permissions: [...draft[role.code]] }).unwrap();
      }
      setDraft({});
      toast.success('Role permissions saved. They apply on each user\'s next request.');
    } catch (err) {
      toast.error(errorMessage(err, 'Could not save the role permissions'));
    }
  };

  if (rolesLoading || catalogLoading) {
    return <div className="space-y-3">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-8 w-full" />)}</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="page-title">Roles & features</h1>
          <p className="max-w-2xl text-sm text-text-muted">
            Each part of the app checks this table. Per-user features can also be switched for one person from
            the Users page; that's how paid features will work later.
          </p>
        </div>
        {editable && (
          <div className="flex shrink-0 items-center gap-2">
            {dirtyRoles.length > 0 && <Button variant="ghost" onClick={() => setDraft({})} disabled={saving}>Discard</Button>}
            <Button onClick={save} disabled={saving || dirtyRoles.length === 0}>
              {dirtyRoles.length > 0 ? `Save ${dirtyRoles.length} role${dirtyRoles.length > 1 ? 's' : ''}` : 'Saved'}
            </Button>
          </div>
        )}
      </div>

      {!editable && (
        <div className="flex items-center gap-2 rounded-[10px] bg-bg-muted px-4 py-3 text-sm text-text-muted">
          <Info className="size-4 shrink-0" />Only the super admin can change roles.
        </div>
      )}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Permission</TableHead>
            {roles.map((role) => (
              <TableHead key={role.code} className="w-[130px] text-center" title={role.description ?? undefined}>
                <div className="flex flex-col items-center">
                  <span>{role.name}</span>
                  <span className="text-[10px] font-normal normal-case text-text-muted">rank {role.rank}</span>
                </div>
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {groups.map(([domain, permissions]) => [
            <TableRow key={domain} className="bg-bg-muted/60 hover:bg-bg-muted/60">
              <TableCell colSpan={roles.length + 1} className="py-2 text-[11px] font-medium uppercase tracking-wider text-text-muted">
                {humanize(domain)}
              </TableCell>
            </TableRow>,
            ...permissions.map((permission) => (
              <TableRow key={permission.code}>
                <TableCell>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm text-text-default">{permission.description}</span>
                    <KindTag kind={permission.kind} />
                  </div>
                  <span className="font-mono text-[11px] text-text-muted">{permission.code}</span>
                </TableCell>
                {roles.map((role) => {
                  const on = granted(role).has(permission.code);
                  const locked = !editable || !role.editable || permission.kind === 'OWNER';
                  const changed = !!draft[role.code] && on !== role.permissions.includes(permission.code);
                  return (
                    <TableCell key={role.code} className="text-center">
                      {locked ? (
                        on
                          ? <Check className="mx-auto size-4 text-brand-primary" aria-label="Granted" />
                          : <span className="text-text-muted" aria-label="Not granted">–</span>
                      ) : (
                        <input
                          type="checkbox"
                          checked={on}
                          onChange={() => toggle(role, permission.code)}
                          aria-label={`${role.name}: ${permission.description}`}
                          className={cn('size-4 cursor-pointer accent-brand-primary', changed && 'outline outline-2 outline-offset-2 outline-status-warning')}
                        />
                      )}
                    </TableCell>
                  );
                })}
              </TableRow>
            )),
          ])}
        </TableBody>
      </Table>
    </div>
  );
}
