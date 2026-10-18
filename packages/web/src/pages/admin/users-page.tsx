import { useState } from 'react';
import { useSelector } from 'react-redux';
import { ChevronLeft, ChevronRight, SlidersHorizontal, UserPlus } from 'lucide-react';
import { toast } from 'sonner';
import type { RootState } from '@/store';
import {
  errorMessage,
  useChangeUserRoleMutation,
  useCreateUserMutation,
  useGetRolesQuery,
  useGetUserFeaturesQuery,
  useListUsersQuery,
  useSetUserFeatureMutation,
  useSetUserStatusMutation,
  type AdminUser,
  type FeatureState,
  type RoleInfo,
} from '@/store/api/admin-api';
import { useCan } from '@/lib/permissions';
import { formatDateShort } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { cn } from 'cn';

const PAGE_SIZE = 20;

function RoleBadge({ role, roles }: { role: string; roles: RoleInfo[] }) {
  const name = roles.find((r) => r.code === role)?.name ?? role;
  return (
    <Badge
      variant={role === 'USER' ? 'neutral' : 'outline'}
      className={cn(role === 'SUPER_ADMIN' && 'border-brand-primary text-brand-primary')}
    >
      {name}
    </Badge>
  );
}

function StatusBadge({ enabled }: { enabled: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 text-sm', enabled ? 'text-text-default' : 'text-status-danger')}>
      <span className={cn('size-1.5 rounded-full', enabled ? 'bg-brand-primary' : 'bg-status-danger')} />
      {enabled ? 'Active' : 'Disabled'}
    </span>
  );
}

export default function AdminUsersPage() {
  const can = useCan();
  const me = useSelector((state: RootState) => state.auth);
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [featuresFor, setFeaturesFor] = useState<AdminUser | null>(null);

  const { data, isLoading } = useListUsersQuery({ page, size: PAGE_SIZE });
  const { data: roles = [] } = useGetRolesQuery();
  const [setStatus] = useSetUserStatusMutation();
  const [changeRole] = useChangeUserRoleMutation();

  const users = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const rankOf = (code: string | null) => roles.find((r) => r.code === code)?.rank ?? 0;
  /** Same rule as the server: never yourself; otherwise only lower ranks, unless you're the super admin. */
  const canManage = (user: AdminUser) =>
    user.id !== me.id && (me.role === 'SUPER_ADMIN' || rankOf(me.role) > rankOf(user.role));

  const toggleStatus = async (user: AdminUser) => {
    try {
      await setStatus({ id: user.id, enabled: !user.enabled }).unwrap();
      toast.success(user.enabled ? `${user.email} disabled` : `${user.email} enabled`);
    } catch (err) {
      toast.error(errorMessage(err, 'Could not change the account status'));
    }
  };

  const updateRole = async (user: AdminUser, role: string) => {
    if (role === user.role) return;
    try {
      await changeRole({ id: user.id, role }).unwrap();
      toast.success(`${user.email} is now ${roles.find((r) => r.code === role)?.name ?? role}`);
    } catch (err) {
      toast.error(errorMessage(err, 'Could not change the role'));
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="page-title">Users</h1>
          <p className="text-sm text-text-muted">
            <span className="tabular-nums">{data?.totalElements ?? 0}</span> accounts. Administrators see accounts only, never anyone's money.
          </p>
        </div>
        {can('USER_CREATE') && (
          <Button onClick={() => setCreating(true)}><UserPlus className="size-4" />New account</Button>
        )}
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Account</TableHead>
            <TableHead className="w-[180px]">Role</TableHead>
            <TableHead className="w-[110px]">Status</TableHead>
            <TableHead className="w-[110px] text-right">Transactions</TableHead>
            <TableHead className="w-[120px]">Last sign-in</TableHead>
            <TableHead className="w-[100px]">Joined</TableHead>
            <TableHead className="w-[200px]" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading && Array.from({ length: 5 }).map((_, i) => (
            <TableRow key={i}><TableCell colSpan={7}><Skeleton className="h-5 w-full" /></TableCell></TableRow>
          ))}
          {!isLoading && users.length === 0 && (
            <TableRow><TableCell colSpan={7} className="py-10 text-center text-text-muted">No accounts yet</TableCell></TableRow>
          )}
          {users.map((user) => {
            const manageable = canManage(user);
            return (
              <TableRow key={user.id}>
                <TableCell>
                  <span className="font-medium text-text-default">{user.email}</span>
                  {user.id === me.id && <span className="ml-2 text-xs text-text-muted">(you)</span>}
                </TableCell>
                <TableCell>
                  {can('ROLE_MANAGE') && manageable ? (
                    <Select value={user.role} onValueChange={(role) => updateRole(user, role)}>
                      <SelectTrigger size="sm" className="w-40"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        {roles.map((r) => <SelectItem key={r.code} value={r.code}>{r.name}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  ) : (
                    <RoleBadge role={user.role} roles={roles} />
                  )}
                </TableCell>
                <TableCell><StatusBadge enabled={user.enabled} /></TableCell>
                <TableCell className="text-right tabular-nums">{user.transactionCount}</TableCell>
                <TableCell className="text-text-muted">{user.lastLoginAt ? formatDateShort(user.lastLoginAt) : 'Never'}</TableCell>
                <TableCell className="text-text-muted">{formatDateShort(user.createdAt)}</TableCell>
                <TableCell>
                  <div className="flex justify-end gap-1.5">
                    {can('FEATURE_MANAGE') && (
                      <Button variant="ghost" size="sm" onClick={() => setFeaturesFor(user)}>
                        <SlidersHorizontal className="size-3.5" />Features
                      </Button>
                    )}
                    {can('USER_SUSPEND') && manageable && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => toggleStatus(user)}
                        className={cn(user.enabled && 'text-status-danger')}
                      >
                        {user.enabled ? 'Disable' : 'Enable'}
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 text-sm text-text-muted">
          <span>Page {page + 1} of {totalPages}</span>
          <Button variant="secondary" size="icon-sm" disabled={page === 0} onClick={() => setPage(page - 1)} aria-label="Previous page">
            <ChevronLeft className="size-4" />
          </Button>
          <Button variant="secondary" size="icon-sm" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)} aria-label="Next page">
            <ChevronRight className="size-4" />
          </Button>
        </div>
      )}

      <CreateUserDialog open={creating} onClose={() => setCreating(false)} roles={roles} canPickRole={can('ROLE_MANAGE')} />
      <FeaturesDialog user={featuresFor} onClose={() => setFeaturesFor(null)} />
    </div>
  );
}

/* ───────────────────────── new account ───────────────────────── */

function CreateUserDialog({ open, onClose, roles, canPickRole }: {
  open: boolean;
  onClose: () => void;
  roles: RoleInfo[];
  canPickRole: boolean;
}) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('USER');
  const [createUser, { isLoading }] = useCreateUserMutation();

  const close = () => {
    setEmail('');
    setPassword('');
    setRole('USER');
    onClose();
  };

  const submit = async () => {
    try {
      await createUser({ email: email.trim(), password, role: canPickRole ? role : undefined }).unwrap();
      toast.success(`Account created for ${email.trim()}`);
      close();
    } catch (err) {
      toast.error(errorMessage(err, 'Could not create the account'));
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) close(); }}>
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>New account</DialogTitle>
          <DialogDescription>Share the password with them privately; they can sign in right away.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label className="field-label" htmlFor="new-email">Email</Label>
            <Input id="new-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label className="field-label" htmlFor="new-password">Initial password</Label>
            <Input id="new-password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
            <p className="text-[11px] text-text-muted">At least 8 characters with upper and lower case, a digit and a symbol.</p>
          </div>
          {canPickRole && (
            <div className="flex flex-col gap-1.5">
              <Label className="field-label">Role</Label>
              <Select value={role} onValueChange={setRole}>
                <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {roles.map((r) => <SelectItem key={r.code} value={r.code}>{r.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="secondary" onClick={close}>Cancel</Button>
          <Button onClick={submit} disabled={isLoading || !email.trim() || !password}>Create account</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/* ───────────────────────── per-user features ───────────────────────── */

type Choice = 'default' | 'on' | 'off';

function choiceOf(feature: FeatureState): Choice {
  if (feature.override === null) return 'default';
  return feature.override ? 'on' : 'off';
}

function FeatureRow({ feature, onChange, disabled }: {
  feature: FeatureState;
  onChange: (enabled: boolean | null) => void;
  disabled: boolean;
}) {
  const current = choiceOf(feature);
  const options: { id: Choice; label: string }[] = [
    { id: 'default', label: `Role (${feature.roleDefault ? 'on' : 'off'})` },
    { id: 'on', label: 'On' },
    { id: 'off', label: 'Off' },
  ];
  return (
    <div className="flex items-center justify-between gap-4 py-3">
      <div className="min-w-0">
        <p className="text-sm font-medium text-text-default">{feature.description}</p>
        <p className="text-xs text-text-muted">
          <span className="font-mono">{feature.permission}</span>
          {' · '}
          <span className={feature.enabled ? 'text-brand-primary' : 'text-status-danger'}>{feature.enabled ? 'On' : 'Off'}</span>
          {feature.override !== null && ' for this user'}
        </p>
      </div>
      <div role="radiogroup" aria-label={feature.description} className="inline-flex shrink-0 gap-1 rounded-[10px] bg-bg-muted p-1">
        {options.map((option) => (
          <button
            key={option.id}
            role="radio"
            aria-checked={current === option.id}
            disabled={disabled}
            onClick={() => onChange(option.id === 'default' ? null : option.id === 'on')}
            className={cn(
              'rounded-[7px] px-2.5 py-1 text-xs font-medium transition-colors disabled:opacity-50',
              current === option.id ? 'bg-bg-surface text-text-default shadow-sm' : 'text-text-muted hover:text-text-default'
            )}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function FeaturesDialog({ user, onClose }: { user: AdminUser | null; onClose: () => void }) {
  const { data: features = [], isFetching } = useGetUserFeaturesQuery(user?.id ?? '', { skip: !user });
  const [setFeature, { isLoading }] = useSetUserFeatureMutation();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';

  const change = async (permission: string, enabled: boolean | null) => {
    if (!user) return;
    try {
      await setFeature({ id: user.id, permission, enabled }).unwrap();
    } catch (err) {
      toast.error(errorMessage(err, 'Could not change the feature'));
    }
  };

  return (
    <Dialog open={!!user} onOpenChange={(o) => { if (!o) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Features</DialogTitle>
          <DialogDescription>
            {isSuperAdmin
              ? `${user?.email} is a super admin and always has every feature.`
              : `Switch features on or off for ${user?.email}, or follow what their role grants.`}
          </DialogDescription>
        </DialogHeader>
        <div className="divide-y divide-border-default">
          {features.map((feature) => (
            <FeatureRow
              key={feature.permission}
              feature={feature}
              disabled={isSuperAdmin || isLoading || isFetching}
              onChange={(enabled) => change(feature.permission, enabled)}
            />
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
