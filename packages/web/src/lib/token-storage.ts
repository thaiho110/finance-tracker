import type { TokenStorage } from '@finance-tracker/shared';
import { desktop } from './platform';

const TOKEN_KEY = 'ft_token';
const REFRESH_KEY = 'ft_refresh_token';

interface SyncTokenStorage extends TokenStorage {
  getToken(): string | null;
  getRefreshToken(): string | null;
  setTokens(token: string, refreshToken: string): void;
  clear(): void;
}

/** Browser: localStorage. */
const browserStorage: SyncTokenStorage = {
  getToken: () => localStorage.getItem(TOKEN_KEY),
  getRefreshToken: () => localStorage.getItem(REFRESH_KEY),
  setTokens: (token, refreshToken) => {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(REFRESH_KEY, refreshToken);
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

/** Desktop: the main process keeps the pair encrypted with Electron safeStorage. */
function desktopStorage(bridge: NonNullable<typeof desktop>): SyncTokenStorage {
  return {
    getToken: () => bridge.tokens.get().token,
    getRefreshToken: () => bridge.tokens.get().refreshToken,
    setTokens: (token, refreshToken) => bridge.tokens.set(token, refreshToken),
    clear: () => bridge.tokens.clear(),
  };
}

/** The platform's TokenStorage (ADR-011). Synchronous on web and desktop. */
export const tokenStorage: SyncTokenStorage = desktop ? desktopStorage(desktop) : browserStorage;
