export type MaybePromise<T> = T | Promise<T>;

/**
 * Where a platform keeps the JWT pair. Each client provides one:
 * web → localStorage, desktop → Electron safeStorage (via preload), mobile → Keychain/Keystore.
 * Methods may be synchronous or asynchronous; the ApiClient awaits them either way.
 */
export interface TokenStorage {
  getToken(): MaybePromise<string | null>;
  getRefreshToken(): MaybePromise<string | null>;
  setTokens(token: string, refreshToken: string): MaybePromise<void>;
  clear(): MaybePromise<void>;
}

/** In-memory storage for tests and for platforms that don't persist sessions. */
export function memoryTokenStorage(): TokenStorage {
  let token: string | null = null;
  let refreshToken: string | null = null;
  return {
    getToken: () => token,
    getRefreshToken: () => refreshToken,
    setTokens: (t, r) => {
      token = t;
      refreshToken = r;
    },
    clear: () => {
      token = null;
      refreshToken = null;
    },
  };
}
