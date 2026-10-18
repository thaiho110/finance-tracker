import { app, BrowserWindow, ipcMain, net, protocol, safeStorage, shell } from 'electron';
import { existsSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { join, normalize, sep } from 'node:path';
import { pathToFileURL } from 'node:url';

/**
 * Kept desktop (ADR-011, phase 11). The window shows the web app; the main process adds
 * what a browser tab can't do safely:
 *  - serves the app from app://finance and forwards app://finance/api/* to the backend, so the UI
 *    calls the API same-origin (no CORS, same code as the web build);
 *  - keeps the JWT pair encrypted with the OS keychain (safeStorage) instead of localStorage.
 */

const SCHEME = 'app';
const HOST = 'finance';
const APP_ORIGIN = `${SCHEME}://${HOST}`;
/** Where the backend runs. Override with FINANCE_TRACKER_API_URL (e.g. your home server). */
const API_URL = (process.env.FINANCE_TRACKER_API_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
const RENDERER_DIR = join(__dirname, '../renderer');

protocol.registerSchemesAsPrivileged([
  { scheme: SCHEME, privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } },
]);

/* ───────── session tokens (safeStorage) ───────── */

interface Session {
  token: string | null;
  refreshToken: string | null;
}

const EMPTY: Session = { token: null, refreshToken: null };
const sessionFile = () => join(app.getPath('userData'), 'session.bin');
let session: Session = EMPTY;

function loadSession(): Session {
  try {
    if (!safeStorage.isEncryptionAvailable() || !existsSync(sessionFile())) return EMPTY;
    return JSON.parse(safeStorage.decryptString(readFileSync(sessionFile()))) as Session;
  } catch {
    return EMPTY;
  }
}

function saveSession(next: Session): void {
  session = next;
  // Without an OS keychain (some Linux setups) the session lives in memory only: never write tokens in plain text.
  if (safeStorage.isEncryptionAvailable()) {
    writeFileSync(sessionFile(), safeStorage.encryptString(JSON.stringify(next)));
  }
}

function clearSession(): void {
  session = EMPTY;
  rmSync(sessionFile(), { force: true });
}

// Synchronous so the shared web code can keep a synchronous TokenStorage (packages/web/src/lib/token-storage.ts).
ipcMain.on('tokens:get', (event) => {
  event.returnValue = session;
});
ipcMain.on('tokens:set', (event, token: string, refreshToken: string) => {
  saveSession({ token, refreshToken });
  event.returnValue = true;
});
ipcMain.on('tokens:clear', (event) => {
  clearSession();
  event.returnValue = true;
});

/* ───────── app:// protocol: static files + API proxy ───────── */

function serve(request: Request): Promise<Response> {
  const url = new URL(request.url);
  if (url.pathname.startsWith('/api/')) {
    const headers = new Headers(request.headers);
    // The backend sees a plain server-to-server call; app:// isn't (and needn't be) a CORS origin.
    headers.delete('origin');
    headers.delete('referer');
    const hasBody = request.method !== 'GET' && request.method !== 'HEAD';
    return net.fetch(API_URL + url.pathname + url.search, {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
      duplex: hasBody ? 'half' : undefined,
    } as RequestInit);
  }
  // Static files; unknown paths get index.html so the router can handle /transactions, /admin/users, …
  let file = normalize(join(RENDERER_DIR, decodeURIComponent(url.pathname)));
  const insideRenderer = file === RENDERER_DIR || file.startsWith(RENDERER_DIR + sep);
  if (!insideRenderer || !existsSync(file) || statSync(file).isDirectory()) {
    file = join(RENDERER_DIR, 'index.html');
  }
  return net.fetch(pathToFileURL(file).toString());
}

/* ───────── window ───────── */

function createWindow(): void {
  const window = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 960,
    minHeight: 640,
    title: 'Kept',
    backgroundColor: '#F6F5F1',
    icon: join(RENDERER_DIR, 'icon-512.png'),
    autoHideMenuBar: true,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
    },
  });

  const devUrl = process.env.ELECTRON_RENDERER_URL;
  const appOrigin = devUrl ? new URL(devUrl).origin : APP_ORIGIN;

  // Links to other sites open in the default browser; the window never navigates away from the app.
  window.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://') || url.startsWith('http://')) void shell.openExternal(url);
    return { action: 'deny' };
  });
  window.webContents.on('will-navigate', (event, url) => {
    if (new URL(url).origin !== appOrigin) event.preventDefault();
  });

  void window.loadURL(devUrl ?? `${APP_ORIGIN}/`);
}

app.whenReady().then(() => {
  session = loadSession();
  protocol.handle(SCHEME, serve);
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
