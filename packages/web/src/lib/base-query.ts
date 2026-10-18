import type { BaseQueryApi, BaseQueryFn, FetchArgs, FetchBaseQueryError } from '@reduxjs/toolkit/query';
import { ApiClient, type ApiRequest } from '@finance-tracker/shared';
import { tokenStorage } from './token-storage';
import { API_BASE_URL, CLIENT_ID } from './platform';
import { logout, setCredentials } from '@/store/auth-slice';

/** The store's dispatch, captured from the latest RTK Query call so the client can update auth state. */
let dispatch: BaseQueryApi['dispatch'] | null = null;

/** The shared API client (ADR-011): token injection, X-Client-Id, one refresh at a time. */
export const apiClient = new ApiClient({
  baseUrl: API_BASE_URL,
  clientId: CLIENT_ID,
  tokenStorage,
  // Keep Redux in sync with storage: the refresh response also carries the user's email, role and permissions.
  onTokensRefreshed: (auth) => dispatch?.(setCredentials(auth)),
  // The session is over; ProtectedRoute then redirects to /login without a full page reload.
  onSessionExpired: () => dispatch?.(logout()),
});

function toRequest(args: string | FetchArgs): ApiRequest {
  if (typeof args === 'string') return { url: args };
  return {
    url: args.url,
    method: (args.method?.toUpperCase() ?? 'GET') as ApiRequest['method'],
    params: args.params as Record<string, unknown> | undefined,
    body: args.body,
    headers: args.headers as Record<string, string> | undefined,
  };
}

export const baseQuery: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> = async (args, api) => {
  dispatch = api.dispatch;
  const result = await apiClient.send(toRequest(args));
  return result.ok
    ? { data: result.data }
    : { error: { status: result.status, data: result.data } as FetchBaseQueryError };
};
