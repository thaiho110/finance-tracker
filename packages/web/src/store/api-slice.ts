import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQuery } from '@/lib/base-query';

export const api = createApi({
  reducerPath: 'api',
  baseQuery,
  tagTypes: ['Transaction', 'Category', 'CategoryRule', 'SystemCategory', 'SystemRule', 'AdminUser', 'Feature', 'Role', 'Audit', 'Overview', 'Budget', 'Registration', 'Invite', 'DeviceKey'],
  endpoints: () => ({}),
});
