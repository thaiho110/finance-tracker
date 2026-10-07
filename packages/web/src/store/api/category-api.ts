import { api } from '../api-slice';

export const categoryApi = api.injectEndpoints({
  endpoints: (builder) => ({
    getCategories: builder.query<string[], void>({
      query: () => '/categories',
      providesTags: ['Category'],
    }),
  }),
});

export const { useGetCategoriesQuery } = categoryApi;
