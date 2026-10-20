import { api } from '../api-slice';

/** A module's state for the current user (ADR-020 §4). */
export interface ModuleState {
  code: string;
  enabled: boolean;
  available: boolean;
}

export const moduleApi = api.injectEndpoints({
  endpoints: (builder) => ({
    modules: builder.query<ModuleState[], void>({
      query: () => '/modules',
      providesTags: ['Module'],
    }),
    setModule: builder.mutation<ModuleState[], { code: string; enabled: boolean }>({
      query: ({ code, enabled }) => ({ url: `/modules/${code}`, method: 'PUT', body: { enabled } }),
      invalidatesTags: ['Module'],
    }),
  }),
});

export const { useModulesQuery, useSetModuleMutation } = moduleApi;
