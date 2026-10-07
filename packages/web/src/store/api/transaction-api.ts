import { api } from '../api-slice';

export const transactionApi = api.injectEndpoints({
  endpoints: (builder) => ({
    getTransactions: builder.query<any, { page?: number; size?: number; category?: string; dateFrom?: string; dateTo?: string; sort?: string }>({
      query: (params) => ({
        url: '/transactions',
        params,
      }),
      providesTags: ['Transaction'],
    }),
    getTransaction: builder.query<any, string>({
      query: (id) => `/transactions/${id}`,
      providesTags: ['Transaction'],
    }),
    updateTransaction: builder.mutation<any, { id: string; data: any }>({
      query: ({ id, data }) => ({
        url: `/transactions/${id}`,
        method: 'PUT',
        body: data,
      }),
      invalidatesTags: ['Transaction'],
    }),
    deleteTransaction: builder.mutation<void, string>({
      query: (id) => ({
        url: `/transactions/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Transaction'],
    }),
    batchSaveTransactions: builder.mutation<any[], any[]>({
      query: (transactions) => ({
        url: '/transactions/batch',
        method: 'POST',
        body: transactions,
      }),
      invalidatesTags: ['Transaction'],
    }),
    parseCsv: builder.mutation<any[], FormData>({
      query: (formData) => ({
        url: '/csv/parse',
        method: 'POST',
        body: formData,
      }),
    }),
    processOcr: builder.mutation<any, FormData>({
      query: (formData) => ({
        url: '/ocr/process',
        method: 'POST',
        body: formData,
      }),
    }),
    getTransactionsSummary: builder.query<any, { from?: string; to?: string }>({
      query: (params) => ({
        url: '/transactions/summary',
        params,
      }),
      providesTags: ['Transaction'],
    }),
  }),
});

export const {
  useGetTransactionsQuery,
  useGetTransactionQuery,
  useUpdateTransactionMutation,
  useDeleteTransactionMutation,
  useBatchSaveTransactionsMutation,
  useParseCsvMutation,
  useProcessOcrMutation,
  useGetTransactionsSummaryQuery,
} = transactionApi;
