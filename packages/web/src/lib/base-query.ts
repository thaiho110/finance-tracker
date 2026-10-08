import type { BaseQueryFn } from '@reduxjs/toolkit/query';
import type { FetchArgs, FetchBaseQueryError } from '@reduxjs/toolkit/query';
import { tokenStorage } from './token-storage';

const BASE_URL = '/api/v1';
const CLIENT_ID = 'finance-tracker-web';

interface RefreshResponse {
  token: string;
  refreshToken: string;
}

let isRefreshing = false;
let pendingRequests: Array<(token: string) => void> = [];

async function refreshTokens(): Promise<string | null> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return null;

  try {
    const res = await fetch(`${BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${refreshToken}`,
        'X-Client-Id': CLIENT_ID,
      },
    });
    if (!res.ok) throw new Error('Refresh failed');
    const data: RefreshResponse = await res.json();
    tokenStorage.setToken(data.token);
    tokenStorage.setRefreshToken(data.refreshToken);
    return data.token;
  } catch {
    tokenStorage.clear();
    window.location.href = '/login';
    return null;
  }
}

function prepareRequest(fetchArgs: FetchArgs): { url: string; method: string; headers: Record<string, string>; body?: BodyInit } {
  const headers = fetchArgs.headers as Record<string, string>;
  if (!headers['Content-Type'] && fetchArgs.body && typeof fetchArgs.body === 'object') {
    headers['Content-Type'] = 'application/json';
  }
  return {
    url: typeof fetchArgs.url === 'string' ? `${BASE_URL}${fetchArgs.url}` : fetchArgs.url,
    method: fetchArgs.method || 'GET',
    headers,
    body: typeof fetchArgs.body === 'object' && fetchArgs.body !== null
      ? JSON.stringify(fetchArgs.body)
      : fetchArgs.body as BodyInit | undefined,
  };
}

export const baseQuery: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  const getArgs = (token: string): FetchArgs => {
    if (typeof args === 'string') {
      return { url: args, headers: { Authorization: `Bearer ${token}`, 'X-Client-Id': CLIENT_ID } };
    }
    return {
      ...args,
      headers: {
        ...args.headers,
        Authorization: `Bearer ${token}`,
        'X-Client-Id': CLIENT_ID,
      },
    };
  };

  const requestUrl = typeof args === 'string' ? args : args.url;
  const isPublicEndpoint = requestUrl.startsWith('/auth/login') || requestUrl.startsWith('/auth/register');

  const token = tokenStorage.getToken();
  if (!token && !isPublicEndpoint) {
    return { error: { status: 401, data: { message: 'No token' } } as FetchBaseQueryError };
  }

  const fetchArgs = token ? getArgs(token) : (typeof args === 'string' ? { url: args, headers: { 'X-Client-Id': CLIENT_ID } } : { ...args, headers: { ...args.headers, 'X-Client-Id': CLIENT_ID } });
  const request = prepareRequest(fetchArgs);
  let result = await fetch(request.url, {
    method: request.method,
    headers: request.headers,
    body: request.body,
  });

  if (result.status === 401) {
    if (!isRefreshing) {
      isRefreshing = true;
      const newToken = await refreshTokens();
      isRefreshing = false;
      if (newToken) {
        pendingRequests.forEach(cb => cb(newToken));
        pendingRequests = [];
        const retryRequest = prepareRequest(getArgs(newToken));
        result = await fetch(retryRequest.url, {
          method: retryRequest.method,
          headers: retryRequest.headers,
          body: retryRequest.body,
        });
      }
    } else {
      return new Promise((resolve) => {
        pendingRequests.push((newToken: string) => {
          const retryRequest = prepareRequest(getArgs(newToken));
          fetch(retryRequest.url, {
            method: retryRequest.method,
            headers: retryRequest.headers,
            body: retryRequest.body,
          }).then(res => {
            resolve({ data: res, meta: { response: res } });
          });
        });
      });
    }
  }

  const data = result.status === 204 ? null : await result.json();
  if (!result.ok) {
    return { error: { status: result.status, data } as FetchBaseQueryError };
  }
  return { data };
};
