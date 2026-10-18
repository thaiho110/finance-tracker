import type { ClientId } from '@finance-tracker/shared';

/**
 * Bridge the desktop app's preload script puts on `window` (packages/desktop/src/preload).
 * Absent in a browser, so the web build keeps using localStorage and `finance-tracker-web`.
 */
export interface DesktopBridge {
  clientId: ClientId;
  platform: string;
  tokens: {
    get(): { token: string | null; refreshToken: string | null };
    set(token: string, refreshToken: string): void;
    clear(): void;
  };
}

declare global {
  interface Window {
    desktop?: DesktopBridge;
  }
}

export const desktop: DesktopBridge | undefined = typeof window !== 'undefined' ? window.desktop : undefined;

export const CLIENT_ID: ClientId = desktop?.clientId ?? 'finance-tracker-web';

/** Same-origin in every build: Vite proxies /api in dev, nginx (web) or the app:// protocol (desktop) in production. */
export const API_BASE_URL = '/api/v1';
