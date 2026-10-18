import { api } from '../api-slice';

/** An account as administrators see it: never amounts or transactions, only a count. */
export interface AdminUser {
  id: string;
  email: string;
  role: string;
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  transactionCount: number;
}

export interface FeatureState {
  permission: string;
  domain: string;
  description: string;
  /** The result for this user. */
  enabled: boolean;
  /** Whether the user's role grants it. */
  roleDefault: boolean;
  /** true/false when switched for this user; null when following the role. */
  override: boolean | null;
}

export interface RoleInfo {
  code: string;
  name: string;
  rank: number;
  description: string | null;
  /** False for SUPER_ADMIN, which always has every permission. */
  editable: boolean;
  permissions: string[];
}

export type PermissionKind = 'ROLE' | 'FEATURE' | 'OWNER';

export interface PermissionInfo {
  code: string;
  domain: string;
  kind: PermissionKind;
  description: string;
}

export interface AuditEntry {
  id: string;
  actorId: string | null;
  actorEmail: string | null;
  action: string;
  target: string | null;
  details: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
}

export const adminApi = api.injectEndpoints({
  endpoints: (builder) => ({
    listUsers: builder.query<Page<AdminUser>, { page: number; size: number }>({
      query: ({ page, size }) => ({ url: '/admin/users', params: { page, size, sort: 'createdAt,desc' } }),
      providesTags: ['AdminUser'],
    }),
    createUser: builder.mutation<AdminUser, { email: string; password: string; role?: string }>({
      query: (body) => ({ url: '/admin/users', method: 'POST', body }),
      invalidatesTags: ['AdminUser', 'Audit'],
    }),
    setUserStatus: builder.mutation<AdminUser, { id: string; enabled: boolean }>({
      query: ({ id, enabled }) => ({ url: `/admin/users/${id}/status`, method: 'PUT', body: { enabled } }),
      invalidatesTags: ['AdminUser', 'Audit'],
    }),
    changeUserRole: builder.mutation<AdminUser, { id: string; role: string }>({
      query: ({ id, role }) => ({ url: `/admin/users/${id}/role`, method: 'PUT', body: { role } }),
      invalidatesTags: (_result, _error, { id }) => ['AdminUser', 'Audit', { type: 'Feature', id }],
    }),
    getUserFeatures: builder.query<FeatureState[], string>({
      query: (id) => `/admin/users/${id}/features`,
      providesTags: (_result, _error, id) => [{ type: 'Feature', id }],
    }),
    setUserFeature: builder.mutation<FeatureState[], { id: string; permission: string; enabled: boolean | null }>({
      query: ({ id, permission, enabled }) => ({
        url: `/admin/users/${id}/features/${permission}`,
        method: 'PUT',
        body: { enabled },
      }),
      invalidatesTags: (_result, _error, { id }) => [{ type: 'Feature', id }, 'Audit'],
    }),
    getRoles: builder.query<RoleInfo[], void>({
      query: () => '/admin/roles',
      providesTags: ['Role'],
    }),
    getPermissionCatalog: builder.query<PermissionInfo[], void>({
      query: () => '/admin/permissions',
    }),
    updateRolePermissions: builder.mutation<RoleInfo, { code: string; permissions: string[] }>({
      query: ({ code, permissions }) => ({ url: `/admin/roles/${code}/permissions`, method: 'PUT', body: { permissions } }),
      invalidatesTags: ['Role', 'Feature', 'Audit'],
    }),
    getAudit: builder.query<Page<AuditEntry>, { page: number; size: number }>({
      query: ({ page, size }) => ({ url: '/admin/audit', params: { page, size } }),
      providesTags: ['Audit'],
    }),
  }),
});

export const {
  useListUsersQuery,
  useCreateUserMutation,
  useSetUserStatusMutation,
  useChangeUserRoleMutation,
  useGetUserFeaturesQuery,
  useSetUserFeatureMutation,
  useGetRolesQuery,
  useGetPermissionCatalogQuery,
  useUpdateRolePermissionsMutation,
  useGetAuditQuery,
} = adminApi;

/** "USER_DISABLED" → "User disabled", "CSV_IMPORT" → "Csv import" (for codes without a nicer label). */
export function humanize(code: string): string {
  const words = code.toLowerCase().split('_').join(' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Problem-detail message from an RTK Query error, or a fallback. */
export function errorMessage(err: unknown, fallback: string): string {
  const data = (err as { data?: { detail?: string } } | undefined)?.data;
  return data?.detail || fallback;
}
