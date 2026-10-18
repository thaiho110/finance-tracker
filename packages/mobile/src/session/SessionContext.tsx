import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { ApiClient, ApiError, can, type Permission, type UserInfo } from '@finance-tracker/shared';
import { keychainTokenStorage } from './token-storage';
import { apiBaseUrl, DEFAULT_SERVER, loadServer, saveServer } from './server';

type Status = 'starting' | 'signedOut' | 'signedIn';

interface Session {
  status: Status;
  user: UserInfo | null;
  server: string;
  api: ApiClient;
  can: (permission: Permission) => boolean;
  signIn: (server: string, email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>('starting');
  const [user, setUser] = useState<UserInfo | null>(null);
  const [server, setServer] = useState(DEFAULT_SERVER);

  const api = useMemo(
    () =>
      new ApiClient({
        baseUrl: apiBaseUrl(DEFAULT_SERVER),
        clientId: 'finance-tracker-mobile',
        tokenStorage: keychainTokenStorage,
        onTokensRefreshed: (auth) => setUser({ id: auth.id, email: auth.email, role: auth.role, permissions: auth.permissions }),
        onSessionExpired: () => {
          setUser(null);
          setStatus('signedOut');
        },
      }),
    [],
  );

  // Restore the session after a restart: tokens are in the keychain, the user comes from /auth/me.
  useEffect(() => {
    (async () => {
      const saved = await loadServer();
      setServer(saved);
      api.setBaseUrl(apiBaseUrl(saved));
      if (!(await keychainTokenStorage.getToken())) {
        setStatus('signedOut');
        return;
      }
      try {
        setUser(await api.auth.me());
        setStatus('signedIn');
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) await keychainTokenStorage.clear();
        setStatus('signedOut');
      }
    })();
  }, [api]);

  const signIn = useCallback(
    async (nextServer: string, email: string, password: string) => {
      api.setBaseUrl(apiBaseUrl(nextServer));
      const auth = await api.auth.login({ email: email.trim(), password });
      await saveServer(nextServer);
      setServer(nextServer.trim().replace(/\/+$/, ''));
      setUser({ id: auth.id, email: auth.email, role: auth.role, permissions: auth.permissions });
      setStatus('signedIn');
    },
    [api],
  );

  const signOut = useCallback(async () => {
    await api.auth.logout();
    setUser(null);
    setStatus('signedOut');
  }, [api]);

  const value = useMemo<Session>(
    () => ({
      status,
      user,
      server,
      api,
      can: (permission) => can(user?.permissions ?? null, permission),
      signIn,
      signOut,
    }),
    [status, user, server, api, signIn, signOut],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession must be used inside SessionProvider');
  return session;
}
