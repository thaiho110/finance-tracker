import * as Keychain from 'react-native-keychain';
import type { TokenStorage } from '@finance-tracker/shared';

const SERVICE = 'com.financetracker.session';

interface Pair {
  token: string | null;
  refreshToken: string | null;
}

let cache: Pair | null = null;

async function load(): Promise<Pair> {
  if (cache) return cache;
  const saved = await Keychain.getGenericPassword({ service: SERVICE });
  cache = saved ? (JSON.parse(saved.password) as Pair) : { token: null, refreshToken: null };
  return cache;
}

/**
 * The JWT pair in the Android Keystore / iOS Keychain (ADR-011), cached in memory so each API call
 * doesn't hit the keychain.
 */
export const keychainTokenStorage: TokenStorage = {
  getToken: async () => (await load()).token,
  getRefreshToken: async () => (await load()).refreshToken,
  setTokens: async (token, refreshToken) => {
    cache = { token, refreshToken };
    await Keychain.setGenericPassword('session', JSON.stringify(cache), {
      service: SERVICE,
      accessible: Keychain.ACCESSIBLE.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
    });
  },
  clear: async () => {
    cache = { token: null, refreshToken: null };
    await Keychain.resetGenericPassword({ service: SERVICE });
  },
};
