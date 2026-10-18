import type { BudgetInput, BudgetRow, Overview } from '@finance-tracker/shared';
import { api } from '../api-slice';

/** The monthly overview and budgets (ADR-019). Setting a budget changes both the budget rows and the overview. */
export const overviewApi = api.injectEndpoints({
  endpoints: (builder) => ({
    /** One month's overview; `month` is `YYYY-MM`, omitted means the current month. */
    getOverview: builder.query<Overview, string | undefined>({
      query: (month) => ({ url: '/overview', params: month ? { month } : undefined }),
      providesTags: ['Overview'],
    }),
    /** The total plus every pickable expense category with its budget and spend for a month. */
    getBudgets: builder.query<BudgetRow[], string | undefined>({
      query: (month) => ({ url: '/budgets', params: month ? { month } : undefined }),
      providesTags: ['Budget'],
    }),
    setBudget: builder.mutation<BudgetRow[], BudgetInput>({
      query: (body) => ({ url: '/budgets', method: 'PUT', body }),
      invalidatesTags: ['Budget', 'Overview'],
    }),
  }),
});

export const { useGetOverviewQuery, useGetBudgetsQuery, useSetBudgetMutation } = overviewApi;
