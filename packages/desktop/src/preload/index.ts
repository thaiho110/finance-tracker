import { contextBridge, ipcRenderer } from 'electron';

/**
 * The only thing the web UI can reach in the desktop app: `window.desktop`
 * (typed in packages/web/src/lib/platform.ts). No Node, no filesystem, no raw IPC.
 */
contextBridge.exposeInMainWorld('desktop', {
  clientId: 'finance-tracker-desktop',
  platform: process.platform,
  tokens: {
    get: (): { token: string | null; refreshToken: string | null } => ipcRenderer.sendSync('tokens:get'),
    set: (token: string, refreshToken: string): void => {
      ipcRenderer.sendSync('tokens:set', token, refreshToken);
    },
    clear: (): void => {
      ipcRenderer.sendSync('tokens:clear');
    },
  },
});
