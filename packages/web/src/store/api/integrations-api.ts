import { api } from '../api-slice';

export interface WebhookEndpoint {
  id: string;
  name: string;
  token: string;
  secret: string;
  eventType: string;
  active: boolean;
  createdAt: string;
}

export interface WebhookSubscription {
  id: string;
  targetUrl: string;
  secret: string;
  eventTypes: string[];
  active: boolean;
  createdAt: string;
}

export interface WebhookDelivery {
  id: string;
  eventType: string;
  status: 'PENDING' | 'DELIVERED' | 'FAILED';
  attempts: number;
  responseCode: number | null;
  lastAttemptAt: string | null;
  createdAt: string;
}

export const integrationsApi = api.injectEndpoints({
  endpoints: (builder) => ({
    webhookEndpoints: builder.query<WebhookEndpoint[], void>({
      query: () => '/integrations/endpoints',
      providesTags: ['WebhookEndpoint'],
    }),
    createWebhookEndpoint: builder.mutation<WebhookEndpoint, { name: string }>({
      query: (body) => ({ url: '/integrations/endpoints', method: 'POST', body }),
      invalidatesTags: ['WebhookEndpoint'],
    }),
    deleteWebhookEndpoint: builder.mutation<void, string>({
      query: (id) => ({ url: `/integrations/endpoints/${id}`, method: 'DELETE' }),
      invalidatesTags: ['WebhookEndpoint'],
    }),
    webhookSubscriptions: builder.query<WebhookSubscription[], void>({
      query: () => '/integrations/subscriptions',
      providesTags: ['WebhookSubscription'],
    }),
    createWebhookSubscription: builder.mutation<WebhookSubscription, { targetUrl: string; eventTypes: string[] }>({
      query: (body) => ({ url: '/integrations/subscriptions', method: 'POST', body }),
      invalidatesTags: ['WebhookSubscription'],
    }),
    deleteWebhookSubscription: builder.mutation<void, string>({
      query: (id) => ({ url: `/integrations/subscriptions/${id}`, method: 'DELETE' }),
      invalidatesTags: ['WebhookSubscription'],
    }),
    webhookDeliveries: builder.query<WebhookDelivery[], string>({
      query: (subscriptionId) => `/integrations/deliveries?subscriptionId=${subscriptionId}`,
    }),
  }),
});

export const {
  useWebhookEndpointsQuery,
  useCreateWebhookEndpointMutation,
  useDeleteWebhookEndpointMutation,
  useWebhookSubscriptionsQuery,
  useCreateWebhookSubscriptionMutation,
  useDeleteWebhookSubscriptionMutation,
  useWebhookDeliveriesQuery,
} = integrationsApi;
