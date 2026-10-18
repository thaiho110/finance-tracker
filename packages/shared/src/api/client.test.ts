import { describe, expect, it, vi } from 'vitest';
import { ApiClient, ApiError, describeProblem } from './client';
import { memoryTokenStorage } from './token-storage';

function json(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function setup(fetchImpl: (url: string, init: RequestInit) => Promise<Response>) {
  const storage = memoryTokenStorage();
  storage.setTokens('access-1', 'refresh-1');
  const onSessionExpired = vi.fn();
  const onTokensRefreshed = vi.fn();
  const fetchMock = vi.fn(fetchImpl);
  const client = new ApiClient({
    baseUrl: 'http://api.test/api/v1/',
    clientId: 'finance-tracker-mobile',
    tokenStorage: storage,
    fetch: fetchMock as unknown as typeof fetch,
    onSessionExpired,
    onTokensRefreshed,
  });
  return { client, storage, fetchMock, onSessionExpired, onTokensRefreshed };
}

const header = (init: RequestInit, name: string) => (init.headers as Record<string, string>)[name];

describe('ApiClient', () => {
  it('adds the client id, bearer token and query string', async () => {
    const { client, fetchMock } = setup(async () => json(200, { content: [] }));

    await client.transactions.list({ page: 0, category: 'Coffee', dateFrom: undefined });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://api.test/api/v1/transactions?page=0&category=Coffee');
    expect(header(init, 'X-Client-Id')).toBe('finance-tracker-mobile');
    expect(header(init, 'Authorization')).toBe('Bearer access-1');
  });

  it('refreshes once for concurrent 401s and retries with the new token', async () => {
    const { client, storage, fetchMock, onTokensRefreshed } = setup(async (url, init) => {
      if (url.endsWith('/auth/refresh')) {
        return json(200, { token: 'access-2', refreshToken: 'refresh-2', id: 'u', email: 'a@b.c', role: 'USER', permissions: [] });
      }
      return header(init, 'Authorization') === 'Bearer access-2' ? json(200, ['Coffee']) : json(401, { detail: 'expired' });
    });

    const [first, second] = await Promise.all([client.categories.list(), client.categories.list()]);

    expect(first).toEqual(['Coffee']);
    expect(second).toEqual(['Coffee']);
    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/auth/refresh'))).toHaveLength(1);
    expect(storage.getToken()).toBe('access-2');
    expect(onTokensRefreshed).toHaveBeenCalledOnce();
  });

  it('clears the session when the refresh fails', async () => {
    const { client, storage, onSessionExpired } = setup(async () => json(401, { detail: 'expired' }));

    await expect(client.categories.list()).rejects.toBeInstanceOf(ApiError);

    expect(storage.getToken()).toBeNull();
    expect(onSessionExpired).toHaveBeenCalledOnce();
  });

  it('does not refresh when a sign-in fails', async () => {
    const { client, fetchMock, onSessionExpired } = setup(async () => json(401, { detail: 'Invalid email or password' }));

    await expect(client.auth.login({ email: 'a@b.c', password: 'x' })).rejects.toThrow('Invalid email or password');

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(header(fetchMock.mock.calls[0][1], 'Authorization')).toBeUndefined();
    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it('stores the tokens after signing in', async () => {
    const { client, storage } = setup(async () =>
      json(200, { token: 'new', refreshToken: 'new-refresh', id: 'u', email: 'a@b.c', role: 'USER', permissions: ['CSV_IMPORT'] }));

    const auth = await client.auth.login({ email: 'a@b.c', password: 'Secret#123' });

    expect(auth.permissions).toEqual(['CSV_IMPORT']);
    expect(storage.getToken()).toBe('new');
    expect(storage.getRefreshToken()).toBe('new-refresh');
  });

  it('sends uploads as multipart without a JSON content type', async () => {
    const { client, fetchMock } = setup(async () => json(200, []));

    await client.imports.parseCsv(new Blob(['date,amount\n'], { type: 'text/csv' }));

    const [, init] = fetchMock.mock.calls[0];
    expect(init.body).toBeInstanceOf(FormData);
    expect(header(init, 'Content-Type')).toBeUndefined();
  });
});

describe('describeProblem', () => {
  it('lists validation errors', () => {
    expect(describeProblem({ detail: 'Validation failed', errors: ['amount: must be greater than 0'] }))
      .toBe('Validation failed: amount: must be greater than 0');
  });
});
