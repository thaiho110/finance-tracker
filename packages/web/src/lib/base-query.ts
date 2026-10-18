import type { BaseQueryApi, BaseQueryFn, FetchArgs, FetchBaseQueryError } from '@reduxjs/toolkit/query';
import { tokenStorage } from './token-storage';
import { logout, setCredentials } from '@/store/auth-slice';

const BASE_URL = '/api/v1';
const CLIENT_ID = 'finance-tracker-web';

/** Endpoints that never carry a token and must not trigger a token refresh on 401 (e.g. wrong password). */
const PUBLIC_ENDPOINTS = ['/auth/login', '/auth/register', '/auth/refresh'];

interface AuthResponse {
  token: string;
  refreshToken: string;
  id: string;
  email: string;
  role: string;
  permissions: string[];
}

type QueryResult = { data: unknown } | { error: FetchBaseQueryError };

/** One refresh at a time: concurrent 401s all wait for the same refresh instead of each starting one. */
let refreshInFlight: Promise<string | null> | null = null;

async function refreshTokens(api: BaseQueryApi): Promise<string | null> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return null;
  try {
    const res = await fetch(`${BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${refreshToken}`, 'X-Client-Id': CLIENT_ID },
    });
    if (!res.ok) return null;
    const data: AuthResponse = await res.json();
    tokenStorage.setToken(data.token);
    tokenStorage.setRefreshToken(data.refreshToken);
    // Keep Redux in sync with storage — the refresh response also carries the user's email and role.
    api.dispatch(setCredentials(data));
    return data.token;
  } catch {
    return null;
  }
}

function refreshOnce(api: BaseQueryApi): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = refreshTokens(api).finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

function urlOf(args: string | FetchArgs): string {
  return typeof args === 'string' ? args : args.url;
}

async function send(args: string | FetchArgs, token: string | null): Promise<Response> {
  const base: FetchArgs = typeof args === 'string' ? { url: args } : args;
  const headers: Record<string, string> = {
    ...(base.headers as Record<string, string> | undefined),
    'X-Client-Id': CLIENT_ID,
  };
  if (token) headers.Authorization = `Bearer ${token}`;

  // FormData (file uploads) must go as-is so the browser sets the multipart boundary.
  const isJsonBody = base.body !== undefined && base.body !== null
    && typeof base.body === 'object' && !(base.body instanceof FormData);
  if (isJsonBody && !headers['Content-Type']) headers['Content-Type'] = 'application/json';

  let url = `${BASE_URL}${base.url}`;
  if (base.params) {
    const query = new URLSearchParams(
      Object.entries(base.params as Record<string, unknown>)
        .filter(([, value]) => value !== undefined && value !== null && value !== '')
        .map(([key, value]) => [key, String(value)])
    ).toString();
    if (query) url += `?${query}`;
  }

  return fetch(url, {
    method: base.method || 'GET',
    headers,
    body: isJsonBody ? JSON.stringify(base.body) : (base.body as BodyInit | undefined),
  });
}

async function toResult(response: Response): Promise<QueryResult> {
  const text = response.status === 204 ? '' : await response.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  return response.ok ? { data } : { error: { status: response.status, data } as FetchBaseQueryError };
}

export const baseQuery: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> = async (args, api) => {
  const isPublic = PUBLIC_ENDPOINTS.some((path) => urlOf(args).startsWith(path));
  const token = tokenStorage.getToken();

  if (!token && !isPublic) {
    return { error: { status: 401, data: { message: 'Not signed in' } } as FetchBaseQueryError };
  }

  let response = await send(args, isPublic ? null : token);

  if (response.status === 401 && !isPublic) {
    const newToken = await refreshOnce(api);
    if (!newToken) {
      // The session is over (refresh token missing, expired or revoked). Clear it; ProtectedRoute
      // then redirects to /login without a full page reload.
      tokenStorage.clear();
      api.dispatch(logout());
      return toResult(response);
    }
    response = await send(args, newToken);
  }

  return toResult(response);
};
