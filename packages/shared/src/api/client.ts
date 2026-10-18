import type {
  AuthResponse,
  Category,
  CategoryChanges,
  CategoryDeleteResult,
  CategoryInput,
  CategoryRule,
  CategoryRuleInput,
  CategoryRulePreview,
  CategoryRuleSaveResult,
  ClientId,
  Credentials,
  Page,
  ParsedReceipt,
  ParsedTransaction,
  ProblemDetail,
  Transaction,
  TransactionFilter,
  TransactionRequest,
  TransactionSummary,
  TransactionUpdateRequest,
  UserInfo,
} from '../types';
import type { TokenStorage } from './token-storage';

export interface ApiRequest {
  /** Path under the base URL, e.g. `/transactions`. */
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  /** Query parameters; undefined, null and empty values are left out. */
  params?: Record<string, unknown>;
  /** JSON-serialized unless it is FormData (file uploads), which goes as-is. */
  body?: unknown;
  headers?: Record<string, string>;
}

export interface ApiResult<T = unknown> {
  ok: boolean;
  status: number;
  data: T;
}

export interface ApiClientOptions {
  /** e.g. `/api/v1` (same origin) or `http://192.168.1.10:8080/api/v1`. May change at runtime via {@link ApiClient.setBaseUrl}. */
  baseUrl: string;
  clientId: ClientId;
  tokenStorage: TokenStorage;
  /** Defaults to the global fetch. */
  fetch?: typeof fetch;
  /** Called with the new pair after a successful token refresh. */
  onTokensRefreshed?: (auth: AuthResponse) => void;
  /** Called once the session can't be refreshed; tokens are already cleared. */
  onSessionExpired?: () => void;
}

/** A failed API call with the server's problem detail. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null, fallback = 'Request failed') {
    super(describeProblem(problem) ?? fallback);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

/** "Validation failed: amount: must be greater than 0" — or the detail alone. */
export function describeProblem(problem: ProblemDetail | null | undefined): string | null {
  if (!problem) return null;
  if (problem.errors?.length) return `${problem.detail ?? 'Validation failed'}: ${problem.errors.join('; ')}`;
  return problem.detail ?? problem.title ?? null;
}

/** Endpoints that never carry a token and must not trigger a refresh on 401 (e.g. wrong password). */
const PUBLIC_PATHS = ['/auth/login', '/auth/register', '/auth/refresh'];

/**
 * HTTP client for the Kept API, shared by every client. It adds `X-Client-Id` and the
 * bearer token, refreshes an expired token once (concurrent 401s share one refresh), and clears the
 * session when the refresh fails. Platform specifics come in through {@link TokenStorage}.
 */
export class ApiClient {
  private baseUrl: string;
  private readonly options: ApiClientOptions;
  private refreshInFlight: Promise<string | null> | null = null;

  constructor(options: ApiClientOptions) {
    this.options = options;
    this.baseUrl = trimSlash(options.baseUrl);
  }

  getBaseUrl(): string {
    return this.baseUrl;
  }

  setBaseUrl(baseUrl: string): void {
    this.baseUrl = trimSlash(baseUrl);
  }

  /** Sends a request and returns status + parsed body without throwing (for RTK Query base queries). */
  async send<T = unknown>(request: ApiRequest): Promise<ApiResult<T>> {
    const isPublic = PUBLIC_PATHS.some((path) => request.url.startsWith(path));
    const token = isPublic ? null : await this.options.tokenStorage.getToken();
    if (!isPublic && !token) {
      return { ok: false, status: 401, data: { detail: 'Not signed in' } as T };
    }

    let response = await this.fetchWith(request, token);
    if (response.status === 401 && !isPublic) {
      const fresh = await this.refreshOnce();
      if (!fresh) {
        return toResult<T>(response);
      }
      response = await this.fetchWith(request, fresh);
    }
    return toResult<T>(response);
  }

  /** Sends a request and returns the body, throwing {@link ApiError} when the response isn't 2xx. */
  async request<T>(request: ApiRequest): Promise<T> {
    const result = await this.send<T>(request);
    if (!result.ok) {
      throw new ApiError(result.status, (result.data as ProblemDetail | null) ?? null);
    }
    return result.data;
  }

  /* ───────── typed endpoints ───────── */

  readonly auth = {
    /** Signs in and stores the tokens. */
    login: async (credentials: Credentials): Promise<AuthResponse> =>
      this.storeSession(await this.request<AuthResponse>({ url: '/auth/login', method: 'POST', body: credentials })),
    register: async (credentials: Credentials): Promise<AuthResponse> =>
      this.storeSession(await this.request<AuthResponse>({ url: '/auth/register', method: 'POST', body: credentials })),
    me: (): Promise<UserInfo> => this.request<UserInfo>({ url: '/auth/me' }),
    logout: async (): Promise<void> => {
      await this.options.tokenStorage.clear();
    },
  };

  readonly transactions = {
    list: (filter: TransactionFilter = {}): Promise<Page<Transaction>> =>
      this.request({ url: '/transactions', params: { ...filter } }),
    get: (id: string): Promise<Transaction> => this.request({ url: `/transactions/${id}` }),
    summary: (): Promise<TransactionSummary> => this.request({ url: '/transactions/summary' }),
    saveBatch: (transactions: TransactionRequest[]): Promise<Transaction[]> =>
      this.request({ url: '/transactions/batch', method: 'POST', body: transactions }),
    update: (id: string, changes: TransactionUpdateRequest): Promise<Transaction> =>
      this.request({ url: `/transactions/${id}`, method: 'PUT', body: changes }),
    remove: async (id: string): Promise<void> => {
      await this.request({ url: `/transactions/${id}`, method: 'DELETE' });
    },
  };

  readonly imports = {
    /** `file` is a FormData-ready value: a File/Blob on web/desktop, `{ uri, name, type }` on React Native. */
    parseCsv: (file: unknown): Promise<ParsedTransaction[]> =>
      this.request({ url: '/csv/parse', method: 'POST', body: formData('file', file) }),
    scanReceipt: (image: unknown): Promise<ParsedReceipt> =>
      this.request({ url: '/ocr/process', method: 'POST', body: formData('image', image) }),
  };

  readonly categories = {
    /** Categories the user can pick; `includeArchived` also returns archived and hidden ones (settings). */
    list: (includeArchived = false): Promise<Category[]> =>
      this.request({ url: '/categories', params: includeArchived ? { includeArchived: true } : undefined }),
    create: (input: CategoryInput): Promise<Category> =>
      this.request({ url: '/categories', method: 'POST', body: input }),
    update: (id: string, changes: CategoryChanges): Promise<Category> =>
      this.request({ url: `/categories/${id}`, method: 'PUT', body: changes }),
    /** Deletes an unused category or archives a used one; `moveTo` moves its transactions first. */
    remove: (id: string, moveTo?: string): Promise<CategoryDeleteResult> =>
      this.request({ url: `/categories/${id}`, method: 'DELETE', params: moveTo ? { moveTo } : undefined }),
    setHidden: async (id: string, hidden: boolean): Promise<void> => {
      await this.request({ url: `/categories/${id}/hide`, method: hidden ? 'POST' : 'DELETE' });
    },
    rules: (scope: 'own' | 'system' = 'own'): Promise<CategoryRule[]> =>
      this.request({ url: '/categories/rules', params: { scope } }),
    previewRule: (keyword: string): Promise<CategoryRulePreview> =>
      this.request({ url: '/categories/rules/preview', params: { keyword } }),
    saveRule: (input: CategoryRuleInput): Promise<CategoryRuleSaveResult> =>
      this.request({ url: '/categories/rules', method: 'POST', body: input }),
    removeRule: async (id: string): Promise<void> => {
      await this.request({ url: `/categories/rules/${id}`, method: 'DELETE' });
    },
  };

  /* ───────── internals ───────── */

  private async storeSession(auth: AuthResponse): Promise<AuthResponse> {
    await this.options.tokenStorage.setTokens(auth.token, auth.refreshToken);
    return auth;
  }

  private fetchWith(request: ApiRequest, token: string | null): Promise<Response> {
    const doFetch = this.options.fetch ?? fetch;
    const headers: Record<string, string> = { ...request.headers, 'X-Client-Id': this.options.clientId };
    if (token) headers.Authorization = `Bearer ${token}`;
    const isForm = typeof FormData !== 'undefined' && request.body instanceof FormData;
    const hasJson = request.body !== undefined && request.body !== null && !isForm;
    if (hasJson && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
    return doFetch(this.baseUrl + request.url + queryString(request.params), {
      method: request.method ?? 'GET',
      headers,
      body: hasJson ? JSON.stringify(request.body) : (request.body as BodyInit | undefined),
    });
  }

  private refreshOnce(): Promise<string | null> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.refresh().finally(() => {
        this.refreshInFlight = null;
      });
    }
    return this.refreshInFlight;
  }

  private async refresh(): Promise<string | null> {
    const storage = this.options.tokenStorage;
    const refreshToken = await storage.getRefreshToken();
    let auth: AuthResponse | null = null;
    if (refreshToken) {
      try {
        const doFetch = this.options.fetch ?? fetch;
        const response = await doFetch(`${this.baseUrl}/auth/refresh`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${refreshToken}`, 'X-Client-Id': this.options.clientId },
        });
        auth = response.ok ? ((await response.json()) as AuthResponse) : null;
      } catch {
        auth = null;
      }
    }
    if (!auth) {
      await storage.clear();
      this.options.onSessionExpired?.();
      return null;
    }
    await storage.setTokens(auth.token, auth.refreshToken);
    this.options.onTokensRefreshed?.(auth);
    return auth.token;
  }
}

function trimSlash(url: string): string {
  return url.replace(/\/+$/, '');
}

function queryString(params: Record<string, unknown> | undefined): string {
  if (!params) return '';
  const pairs = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  return pairs.length ? `?${pairs.join('&')}` : '';
}

function formData(field: string, file: unknown): FormData {
  const form = new FormData();
  // React Native's FormData accepts { uri, name, type }; browsers accept File/Blob.
  form.append(field, file as Blob);
  return form;
}

async function toResult<T>(response: Response): Promise<ApiResult<T>> {
  const text = response.status === 204 ? '' : await response.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  return { ok: response.ok, status: response.status, data: data as T };
}
