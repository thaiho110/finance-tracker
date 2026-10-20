import { api } from '../api-slice';

/** One row of the in-app notification feed (ADR-021). */
export interface NotificationRow {
  id: string;
  type: string;
  title: string;
  body: string | null;
  deepLink: string | null;
  read: boolean;
  createdAt: string;
}

export const notificationApi = api.injectEndpoints({
  endpoints: (builder) => ({
    notifications: builder.query<NotificationRow[], { unread?: boolean } | void>({
      query: (arg) => (arg && arg.unread ? '/notifications?unread=true' : '/notifications'),
      providesTags: ['Notification'],
    }),
    unreadCount: builder.query<{ count: number }, void>({
      query: () => '/notifications/unread-count',
      providesTags: ['Notification'],
    }),
    markNotificationRead: builder.mutation<void, string>({
      query: (id) => ({ url: `/notifications/${id}/read`, method: 'POST' }),
      invalidatesTags: ['Notification'],
    }),
    markAllNotificationsRead: builder.mutation<void, void>({
      query: () => ({ url: '/notifications/read-all', method: 'POST' }),
      invalidatesTags: ['Notification'],
    }),
  }),
});

export const {
  useNotificationsQuery,
  useUnreadCountQuery,
  useMarkNotificationReadMutation,
  useMarkAllNotificationsReadMutation,
} = notificationApi;
