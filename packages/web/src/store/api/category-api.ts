import type {
  Category,
  CategoryChanges,
  CategoryDeleteResult,
  CategoryInput,
  CategoryRule,
  CategoryRuleInput,
  CategoryRulePreview,
  CategoryRuleSaveResult,
} from '@finance-tracker/shared';
import { api } from '../api-slice';

/** Categories and keyword rules (ADR-015). Changing them can re-categorize transactions, so those refetch too. */
export const categoryApi = api.injectEndpoints({
  endpoints: (builder) => ({
    /** Pickers: defaults the user didn't hide plus their own, without archived ones. */
    getCategories: builder.query<Category[], void>({
      query: () => '/categories',
      providesTags: ['Category'],
    }),
    /** Settings: everything, archived and hidden included. */
    getAllCategories: builder.query<Category[], void>({
      query: () => ({ url: '/categories', params: { includeArchived: true } }),
      providesTags: ['Category'],
    }),
    createCategory: builder.mutation<Category, CategoryInput>({
      query: (body) => ({ url: '/categories', method: 'POST', body }),
      invalidatesTags: ['Category'],
    }),
    updateCategory: builder.mutation<Category, { id: string; changes: CategoryChanges }>({
      query: ({ id, changes }) => ({ url: `/categories/${id}`, method: 'PUT', body: changes }),
      invalidatesTags: ['Category', 'Transaction'],
    }),
    deleteCategory: builder.mutation<CategoryDeleteResult, { id: string; moveTo?: string }>({
      query: ({ id, moveTo }) => ({ url: `/categories/${id}`, method: 'DELETE', params: moveTo ? { moveTo } : undefined }),
      invalidatesTags: ['Category', 'Transaction'],
    }),
    setCategoryHidden: builder.mutation<void, { id: string; hidden: boolean }>({
      query: ({ id, hidden }) => ({ url: `/categories/${id}/hide`, method: hidden ? 'POST' : 'DELETE' }),
      invalidatesTags: ['Category'],
    }),
    getRules: builder.query<CategoryRule[], 'own' | 'system'>({
      query: (scope) => ({ url: '/categories/rules', params: { scope } }),
      providesTags: ['CategoryRule'],
    }),
    saveRule: builder.mutation<CategoryRuleSaveResult, CategoryRuleInput>({
      query: (body) => ({ url: '/categories/rules', method: 'POST', body }),
      invalidatesTags: (result) => (result && result.moved > 0 ? ['CategoryRule', 'Transaction'] : ['CategoryRule']),
    }),
    deleteRule: builder.mutation<void, string>({
      query: (id) => ({ url: `/categories/rules/${id}`, method: 'DELETE' }),
      invalidatesTags: ['CategoryRule'],
    }),
    /** How many past transactions in Other a keyword would move (asked before "also move them?"). */
    previewRule: builder.mutation<CategoryRulePreview, string>({
      query: (keyword) => ({ url: '/categories/rules/preview', params: { keyword } }),
    }),

    /* ── defaults everyone shares (CATEGORY_ADMIN) ── */
    getSystemCategories: builder.query<Category[], void>({
      query: () => '/admin/categories',
      providesTags: ['SystemCategory'],
    }),
    createSystemCategory: builder.mutation<Category, CategoryInput>({
      query: (body) => ({ url: '/admin/categories', method: 'POST', body }),
      invalidatesTags: ['SystemCategory', 'Category', 'Audit'],
    }),
    updateSystemCategory: builder.mutation<Category, { id: string; changes: CategoryChanges }>({
      query: ({ id, changes }) => ({ url: `/admin/categories/${id}`, method: 'PUT', body: changes }),
      invalidatesTags: ['SystemCategory', 'Category', 'Transaction', 'Audit'],
    }),
    getSystemRules: builder.query<CategoryRule[], void>({
      query: () => '/admin/categories/rules',
      providesTags: ['SystemRule'],
    }),
    saveSystemRule: builder.mutation<CategoryRule, { keyword: string; categoryId: string }>({
      query: (body) => ({ url: '/admin/categories/rules', method: 'POST', body }),
      invalidatesTags: ['SystemRule', 'Audit'],
    }),
    deleteSystemRule: builder.mutation<void, string>({
      query: (id) => ({ url: `/admin/categories/rules/${id}`, method: 'DELETE' }),
      invalidatesTags: ['SystemRule', 'Audit'],
    }),
  }),
});

export const {
  useGetCategoriesQuery,
  useGetAllCategoriesQuery,
  useCreateCategoryMutation,
  useUpdateCategoryMutation,
  useDeleteCategoryMutation,
  useSetCategoryHiddenMutation,
  useGetRulesQuery,
  useSaveRuleMutation,
  useDeleteRuleMutation,
  usePreviewRuleMutation,
  useGetSystemCategoriesQuery,
  useCreateSystemCategoryMutation,
  useUpdateSystemCategoryMutation,
  useGetSystemRulesQuery,
  useSaveSystemRuleMutation,
  useDeleteSystemRuleMutation,
} = categoryApi;
