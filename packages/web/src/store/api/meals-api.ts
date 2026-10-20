import { api } from '../api-slice';

export type MealResponse = 'COMING' | 'LATER' | 'SKIP';

export interface MealAck {
  userId: string;
  response: MealResponse;
  respondedAt: string;
}

export interface MealCall {
  id: string;
  callerUserId: string;
  message: string | null;
  createdAt: string;
  responses: MealAck[];
}

export const mealsApi = api.injectEndpoints({
  endpoints: (builder) => ({
    mealCalls: builder.query<MealCall[], void>({
      query: () => '/meals/calls',
      providesTags: ['MealCall'],
    }),
    callMeal: builder.mutation<MealCall, { message?: string }>({
      query: (body) => ({ url: '/meals/calls', method: 'POST', body }),
      invalidatesTags: ['MealCall'],
    }),
    respondToMeal: builder.mutation<MealCall, { id: string; response: MealResponse }>({
      query: ({ id, response }) => ({ url: `/meals/calls/${id}/respond`, method: 'POST', body: { response } }),
      invalidatesTags: ['MealCall'],
    }),
  }),
});

export const { useMealCallsQuery, useCallMealMutation, useRespondToMealMutation } = mealsApi;
