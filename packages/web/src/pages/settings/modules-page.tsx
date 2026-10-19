import { toast } from 'sonner';
import { SlidersHorizontal } from 'lucide-react';
import { useDispatch } from 'react-redux';
import { cn } from 'cn';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useModulesQuery, useSetModuleMutation } from '@/store/api/module-api';
import { authApi } from '@/store/api/auth-api';
import { setCredentials } from '@/store/auth-slice';
import { tokenStorage } from '@/lib/token-storage';
import type { AppDispatch } from '@/store';

const LABELS: Record<string, string> = {
  SPENDING: 'Spending',
  ASSISTANT: 'Assistant',
  INTEGRATIONS: 'Integrations',
  MEALS: 'Meals',
};

export default function ModulesPage() {
  const { data: modules = [] } = useModulesQuery();
  const [setModule, { isLoading }] = useSetModuleMutation();
  const dispatch = useDispatch<AppDispatch>();

  const toggle = async (code: string, enabled: boolean) => {
    try {
      await setModule({ code, enabled }).unwrap();
    } catch {
      toast.error('At least one module must stay on.');
      return;
    }
    // Refresh effective permissions so the sidebar updates right away.
    const token = tokenStorage.getToken();
    const refreshToken = tokenStorage.getRefreshToken();
    if (token && refreshToken) {
      const user = await dispatch(authApi.endpoints.me.initiate(undefined, { forceRefetch: true })).unwrap();
      dispatch(setCredentials({ token, refreshToken, id: user.id, email: user.email, role: user.role, permissions: user.permissions }));
    }
  };

  return (
    <div className="mx-auto max-w-2xl">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><SlidersHorizontal className="size-4" /> Modules</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-text-muted">Choose which parts of Kept you use. Turning one off hides it; your data stays and comes back if you turn it on again.</p>
          {modules.map((module) => (
            <div
              key={module.code}
              className={cn(
                'flex items-center justify-between rounded-lg border border-border-default p-3',
                !module.available && 'opacity-50'
              )}
            >
              <div>
                <p className="text-sm font-medium text-text-default">{LABELS[module.code] ?? module.code}</p>
                {!module.available && <p className="text-xs text-text-muted">Not available on your account.</p>}
              </div>
              <Button
                variant={module.enabled ? 'default' : 'secondary'}
                size="sm"
                disabled={isLoading || !module.available}
                onClick={() => toggle(module.code, !module.enabled)}
              >
                {module.enabled ? 'On' : 'Off'}
              </Button>
            </div>
          ))}
          {modules.length === 0 && <p className="text-sm text-text-muted">No modules.</p>}
        </CardContent>
      </Card>
    </div>
  );
}
