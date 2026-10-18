import { useState } from 'react';
import { toast } from 'sonner';
import {
  useGetRegistrationModeQuery,
  useSetRegistrationModeMutation,
  useListInvitesQuery,
  useCreateInviteMutation,
  useRevokeInviteMutation,
  useListUsersQuery,
  useListDeviceKeysQuery,
  useCreateDeviceKeyMutation,
  useRevokeDeviceKeyMutation,
  errorMessage,
  type RegistrationMode,
} from '@/store/api/admin-api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Copy, Trash2, Plus } from 'lucide-react';
import { useCan } from '@/lib/permissions';
import { cn } from 'cn';

const MODES: { value: RegistrationMode; label: string; help: string }[] = [
  { value: 'OPEN', label: 'Open', help: 'Anyone can create an account.' },
  { value: 'INVITE_ONLY', label: 'Invite only', help: 'Only people with an invite link can register.' },
  { value: 'CLOSED', label: 'Closed', help: 'No one can register.' },
];

function inviteLink(token: string): string {
  return `${window.location.origin}/register?invite=${encodeURIComponent(token)}`;
}

export default function AdminSettingsPage() {
  const can = useCan();
  const canManageMode = can('ROLE_MANAGE');
  const canManageInvites = can('USER_CREATE');
  const canReadUsers = can('USER_READ');

  const { data: mode, isLoading: modeLoading } = useGetRegistrationModeQuery();
  const [setMode, { isLoading: settingMode }] = useSetRegistrationModeMutation();
  const { data: invites, isLoading: invitesLoading } = useListInvitesQuery();
  const [createInvite, { isLoading: creating }] = useCreateInviteMutation();
  const [revokeInvite] = useRevokeInviteMutation();
  const [lastToken, setLastToken] = useState<string | null>(null);

  const { data: deviceKeys, isLoading: keysLoading } = useListDeviceKeysQuery(undefined, { skip: !canReadUsers });
  const { data: usersPage } = useListUsersQuery({ page: 0, size: 100 }, { skip: !canManageInvites });
  const [createDeviceKey, { isLoading: creatingKey }] = useCreateDeviceKeyMutation();
  const [revokeDeviceKey] = useRevokeDeviceKeyMutation();
  const [keyLabel, setKeyLabel] = useState('');
  const [keyOwner, setKeyOwner] = useState('');
  const [lastKey, setLastKey] = useState<string | null>(null);

  async function changeMode(next: RegistrationMode) {
    if (next === mode?.mode) return;
    try {
      await setMode(next).unwrap();
      toast.success('Registration mode updated');
    } catch (err) {
      toast.error(errorMessage(err, 'Could not update the registration mode'));
    }
  }

  async function makeInvite() {
    try {
      const created = await createInvite().unwrap();
      setLastToken(created.token);
      toast.success('Invite created — copy the link now, it is shown only once');
    } catch (err) {
      toast.error(errorMessage(err, 'Could not create an invite'));
    }
  }

  async function copyLink(token: string) {
    try {
      await navigator.clipboard.writeText(inviteLink(token));
      toast.success('Invite link copied');
    } catch {
      toast.error('Could not copy; select and copy manually');
    }
  }

  async function makeDeviceKey() {
    if (!keyLabel.trim() || !keyOwner) {
      toast.error('Enter a label and choose an owner');
      return;
    }
    try {
      const created = await createDeviceKey({ userId: keyOwner, label: keyLabel.trim() }).unwrap();
      setLastKey(created.key);
      setKeyLabel('');
      setKeyOwner('');
      toast.success('Device key created — copy it now, it is shown only once');
    } catch (err) {
      toast.error(errorMessage(err, 'Could not create a device key'));
    }
  }

  async function copyKey(key: string) {
    try {
      await navigator.clipboard.writeText(key);
      toast.success('Device key copied');
    } catch {
      toast.error('Could not copy; select and copy manually');
    }
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Registration</CardTitle>
        </CardHeader>
        <CardContent>
          {modeLoading ? (
            <Skeleton className="h-24" />
          ) : (
            <div className="space-y-3">
              {MODES.map((m) => {
                const active = mode?.mode === m.value;
                return (
                  <button
                    key={m.value}
                    disabled={!canManageMode || settingMode}
                    onClick={() => changeMode(m.value)}
                    className={cn(
                      'flex w-full items-start gap-3 rounded-[10px] border p-4 text-left transition-colors',
                      active ? 'border-brand-primary bg-brand-primary-soft' : 'border-border-default hover:bg-bg-muted',
                      !canManageMode && 'cursor-not-allowed opacity-70'
                    )}
                  >
                    <span className={cn('mt-0.5 size-4 shrink-0 rounded-full border-2', active ? 'border-brand-primary bg-brand-primary' : 'border-border-default')} />
                    <span>
                      <span className="block text-sm font-medium text-text-default">{m.label}</span>
                      <span className="block text-xs text-text-muted">{m.help}</span>
                    </span>
                  </button>
                );
              })}
              {!canManageMode && <p className="text-xs text-text-muted">Only the super admin can change this.</p>}
            </div>
          )}
        </CardContent>
      </Card>

      {canManageInvites && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Invites</CardTitle>
              <Button size="sm" onClick={makeInvite} disabled={creating}>
                <Plus className="size-4" />
                New invite
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            {lastToken && (
              <div className="mb-4 rounded-[10px] border border-brand-primary bg-brand-primary-soft p-3">
                <p className="mb-1 text-xs font-medium text-text-default">New invite link — copy it now, it won't be shown again:</p>
                <div className="flex items-center gap-2">
                  <code className="flex-1 truncate rounded bg-bg-surface px-2 py-1 text-xs text-text-default">{inviteLink(lastToken)}</code>
                  <Button size="icon-sm" variant="ghost" onClick={() => copyLink(lastToken)}><Copy className="size-4" /></Button>
                </div>
              </div>
            )}
            {invitesLoading ? (
              <Skeleton className="h-24" />
            ) : invites && invites.length > 0 ? (
              <div className="divide-y divide-border-default">
                {invites.map((inv) => {
                  const expired = new Date(inv.expiresAt) < new Date();
                  const status = inv.used ? 'Used' : expired ? 'Expired' : 'Active';
                  return (
                    <div key={inv.id} className="flex items-center justify-between py-3">
                      <div>
                        <p className="text-sm text-text-default">
                          <span className={cn('font-medium', status === 'Active' ? 'text-brand-primary' : 'text-text-muted')}>{status}</span>
                          <span className="text-text-muted"> · created {new Date(inv.createdAt).toLocaleDateString()}</span>
                        </p>
                        <p className="text-xs text-text-muted">
                          {inv.used ? `Used ${inv.usedAt ? new Date(inv.usedAt).toLocaleDateString() : ''}` : `Expires ${new Date(inv.expiresAt).toLocaleDateString()}`}
                        </p>
                      </div>
                      {!inv.used && !expired && (
                        <Button size="icon-sm" variant="ghost" onClick={() => revokeInvite(inv.id)} title="Revoke" className="text-text-muted hover:text-status-danger">
                          <Trash2 className="size-4" />
                        </Button>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="py-8 text-center text-sm text-text-muted">No invites yet. Create one to let someone register.</p>
            )}
          </CardContent>
        </Card>
      )}

      {canReadUsers && (
        <Card>
          <CardHeader>
            <CardTitle>Device keys</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="mb-4 text-xs text-text-muted">
              A device key lets a device submit data as its owner, using the <code>X-Api-Key</code> header. Keys act with
              that user's data permissions only — never admin access — and are stored hashed, so they are shown once.
            </p>
            {canManageInvites && (
              <div className="mb-4 flex flex-col gap-3 rounded-[10px] border border-border-default p-4 sm:flex-row sm:items-end">
                <div className="flex-1 space-y-1">
                  <Label htmlFor="key-label">Label</Label>
                  <Input id="key-label" value={keyLabel} onChange={(e) => setKeyLabel(e.target.value)} placeholder="Kitchen tablet" maxLength={100} />
                </div>
                <div className="flex-1 space-y-1">
                  <Label htmlFor="key-owner">Owner</Label>
                  <Select value={keyOwner} onValueChange={setKeyOwner}>
                    <SelectTrigger id="key-owner" className="w-full"><SelectValue placeholder="Choose a user" /></SelectTrigger>
                    <SelectContent>
                      {usersPage?.content.map((u) => <SelectItem key={u.id} value={u.id}>{u.email}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
                <Button onClick={makeDeviceKey} disabled={creatingKey}>
                  <Plus className="size-4" />
                  Create
                </Button>
              </div>
            )}
            {lastKey && (
              <div className="mb-4 rounded-[10px] border border-brand-primary bg-brand-primary-soft p-3">
                <p className="mb-1 text-xs font-medium text-text-default">New device key — copy it now, it won't be shown again:</p>
                <div className="flex items-center gap-2">
                  <code className="flex-1 truncate rounded bg-bg-surface px-2 py-1 text-xs text-text-default">{lastKey}</code>
                  <Button size="icon-sm" variant="ghost" onClick={() => copyKey(lastKey)}><Copy className="size-4" /></Button>
                </div>
              </div>
            )}
            {keysLoading ? (
              <Skeleton className="h-24" />
            ) : deviceKeys && deviceKeys.length > 0 ? (
              <div className="divide-y divide-border-default">
                {deviceKeys.map((key) => {
                  const status = key.revoked ? 'Revoked' : 'Active';
                  return (
                    <div key={key.id} className="flex items-center justify-between py-3">
                      <div>
                        <p className="text-sm text-text-default">
                          <span className={cn('font-medium', status === 'Active' ? 'text-brand-primary' : 'text-text-muted')}>{key.label}</span>
                          <span className="text-text-muted"> · {key.ownerEmail ?? 'unknown user'}</span>
                        </p>
                        <p className="text-xs text-text-muted">
                          {status} · created {new Date(key.createdAt).toLocaleDateString()}
                          {key.lastUsedAt ? ` · last used ${new Date(key.lastUsedAt).toLocaleDateString()}` : ' · never used'}
                        </p>
                      </div>
                      {canManageInvites && !key.revoked && (
                        <Button size="icon-sm" variant="ghost" onClick={() => revokeDeviceKey(key.id)} title="Revoke" className="text-text-muted hover:text-status-danger">
                          <Trash2 className="size-4" />
                        </Button>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="py-8 text-center text-sm text-text-muted">No device keys yet.</p>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
