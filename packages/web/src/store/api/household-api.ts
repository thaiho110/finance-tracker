import { api } from '../api-slice';

export interface HouseholdMember {
  userId: string;
  email: string | null;
  joinedAt: string;
}

export interface Household {
  id: string;
  name: string;
  createdAt: string;
  members: HouseholdMember[];
}

export const householdApi = api.injectEndpoints({
  endpoints: (builder) => ({
    // 204 (not in a household) comes back as null.
    household: builder.query<Household | null, void>({
      query: () => '/household',
      providesTags: ['Household'],
    }),
    createHousehold: builder.mutation<Household, { name: string }>({
      query: (body) => ({ url: '/household', method: 'POST', body }),
      invalidatesTags: ['Household'],
    }),
    inviteToHousehold: builder.mutation<{ code: string; expiresAt: string }, { email: string }>({
      query: (body) => ({ url: '/household/invites', method: 'POST', body }),
    }),
    joinHousehold: builder.mutation<Household, { code: string }>({
      query: (body) => ({ url: '/household/join', method: 'POST', body }),
      invalidatesTags: ['Household'],
    }),
    leaveHousehold: builder.mutation<void, void>({
      query: () => ({ url: '/household/members/me', method: 'DELETE' }),
      invalidatesTags: ['Household', 'MealCall'],
    }),
  }),
});

export const {
  useHouseholdQuery,
  useCreateHouseholdMutation,
  useInviteToHouseholdMutation,
  useJoinHouseholdMutation,
  useLeaveHouseholdMutation,
} = householdApi;
