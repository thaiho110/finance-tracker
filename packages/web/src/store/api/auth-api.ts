import { api } from '../api-slice';

export const authApi = api.injectEndpoints({
  endpoints: (builder) => ({
    /** The signed-in user; used on startup to restore the session after a page reload. */
    me: builder.query<{ email: string; role: string }, void>({
      query: () => '/auth/me',
    }),
    login: builder.mutation<{ token: string; refreshToken: string; email: string; role: string }, { email: string; password: string }>({
      query: (body) => ({
        url: '/auth/login',
        method: 'POST',
        body,
      }),
    }),
    register: builder.mutation<{ token: string; refreshToken: string; email: string; role: string }, { email: string; password: string }>({
      query: (body) => ({
        url: '/auth/register',
        method: 'POST',
        body,
      }),
    }),
  }),
});

export const { useLoginMutation, useRegisterMutation, useMeQuery } = authApi;
