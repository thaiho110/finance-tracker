import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQuery } from '@/lib/base-query';

export const api = createApi({
  reducerPath: 'api',
  baseQuery,
  tagTypes: ['Transaction', 'Category'],
  endpoints: () => ({}),
});
