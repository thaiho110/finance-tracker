import { Platform } from 'react-native';
import * as Keychain from 'react-native-keychain';

const SERVICE = 'com.financetracker.server';

/**
 * Where the backend is. The Android emulator reaches the host as 10.0.2.2; a real phone needs the
 * computer's LAN address (e.g. http://192.168.1.20:8080), set on the sign-in screen.
 */
export const DEFAULT_SERVER = Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';

export function apiBaseUrl(server: string): string {
  return `${server.trim().replace(/\/+$/, '')}/api/v1`;
}

export async function loadServer(): Promise<string> {
  const saved = await Keychain.getGenericPassword({ service: SERVICE });
  return saved ? saved.password : DEFAULT_SERVER;
}

export async function saveServer(server: string): Promise<void> {
  await Keychain.setGenericPassword('server', server.trim().replace(/\/+$/, ''), { service: SERVICE });
}
