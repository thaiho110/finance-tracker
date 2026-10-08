import { api } from '../api-slice';

export const authApi = api.injectEndpoints({
  endpoints: (builder) => ({
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

export const { useLoginMutation, useRegisterMutation } = authApi;
