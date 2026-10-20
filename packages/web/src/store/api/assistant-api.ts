import { api } from '../api-slice';

export interface AssistantThread {
  id: string;
  title: string | null;
  createdAt: string;
}

export type MessageRole = 'USER' | 'ASSISTANT' | 'TOOL';

export interface AssistantMessage {
  id: string;
  role: MessageRole;
  content: string;
  createdAt: string;
}

export const assistantApi = api.injectEndpoints({
  endpoints: (builder) => ({
    threads: builder.query<AssistantThread[], void>({
      query: () => '/assistant/threads',
      providesTags: ['AssistantThread'],
    }),
    createThread: builder.mutation<AssistantThread, { title?: string }>({
      query: (body) => ({ url: '/assistant/threads', method: 'POST', body }),
      invalidatesTags: ['AssistantThread'],
    }),
    threadMessages: builder.query<AssistantMessage[], string>({
      query: (threadId) => `/assistant/threads/${threadId}/messages`,
      providesTags: (_result, _error, threadId) => [{ type: 'AssistantMessage', id: threadId }],
    }),
    postAssistantMessage: builder.mutation<AssistantMessage, { threadId: string; content: string }>({
      query: ({ threadId, content }) => ({
        url: `/assistant/threads/${threadId}/messages`,
        method: 'POST',
        body: { content },
      }),
      invalidatesTags: (_result, _error, { threadId }) => [{ type: 'AssistantMessage', id: threadId }],
    }),
  }),
});

export const {
  useThreadsQuery,
  useCreateThreadMutation,
  useThreadMessagesQuery,
  usePostAssistantMessageMutation,
} = assistantApi;
