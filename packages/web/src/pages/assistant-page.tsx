import { useState } from 'react';
import { Plus, Send, MessageSquare, Loader2 } from 'lucide-react';
import { cn } from 'cn';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  useThreadsQuery,
  useCreateThreadMutation,
  useThreadMessagesQuery,
  usePostAssistantMessageMutation,
} from '@/store/api/assistant-api';

export default function AssistantPage() {
  const { data: threads = [] } = useThreadsQuery();
  const [createThread] = useCreateThreadMutation();
  const [postMessage, { isLoading: sending }] = usePostAssistantMessageMutation();
  const [activeId, setActiveId] = useState<string | null>(null);
  const [input, setInput] = useState('');

  const currentId = activeId ?? threads[0]?.id ?? null;
  const { data: messages = [], isFetching } = useThreadMessagesQuery(currentId as string, { skip: !currentId });

  const newThread = async () => {
    const thread = await createThread({}).unwrap();
    setActiveId(thread.id);
  };

  const send = async () => {
    const text = input.trim();
    if (!text || sending) return;
    let threadId = currentId;
    if (!threadId) {
      const thread = await createThread({}).unwrap();
      threadId = thread.id;
      setActiveId(thread.id);
    }
    setInput('');
    await postMessage({ threadId, content: text }).unwrap();
  };

  return (
    <div className="flex h-[calc(100vh-7rem)] gap-4">
      {/* Threads */}
      <aside className="hidden w-60 shrink-0 flex-col rounded-[12px] border border-border-default bg-bg-surface p-3 md:flex">
        <Button variant="secondary" size="sm" className="mb-3 justify-start gap-2" onClick={newThread}>
          <Plus className="size-4" /> New chat
        </Button>
        <div className="flex-1 space-y-1 overflow-y-auto">
          {threads.map((thread) => (
            <button
              key={thread.id}
              onClick={() => setActiveId(thread.id)}
              className={cn(
                'flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm',
                thread.id === currentId ? 'bg-brand-primary-soft text-brand-primary' : 'text-text-muted hover:bg-bg-muted'
              )}
            >
              <MessageSquare className="size-4 shrink-0" />
              <span className="truncate">{thread.title || 'Untitled'}</span>
            </button>
          ))}
          {threads.length === 0 && <p className="px-3 py-2 text-sm text-text-muted">No chats yet.</p>}
        </div>
      </aside>

      {/* Conversation */}
      <section className="flex flex-1 flex-col rounded-[12px] border border-border-default bg-bg-surface">
        <div className="flex-1 space-y-4 overflow-y-auto p-5">
          {!currentId && (
            <div className="mt-20 text-center text-text-muted">
              <MessageSquare className="mx-auto mb-2 size-8 opacity-50" />
              <p>Ask the assistant anything about your finances or household.</p>
            </div>
          )}
          {isFetching && messages.length === 0 && (
            <Loader2 className="mx-auto mt-20 size-5 animate-spin text-text-muted" />
          )}
          {messages.map((message) => (
            <div key={message.id} className={cn('flex', message.role === 'USER' ? 'justify-end' : 'justify-start')}>
              <div
                className={cn(
                  'max-w-[80%] whitespace-pre-wrap rounded-2xl px-4 py-2 text-sm',
                  message.role === 'USER'
                    ? 'bg-brand-primary text-white'
                    : 'bg-bg-muted text-text-default'
                )}
              >
                {message.content}
              </div>
            </div>
          ))}
          {sending && <Loader2 className="size-4 animate-spin text-text-muted" />}
        </div>

        <form
          className="flex items-center gap-2 border-t border-border-default p-3"
          onSubmit={(event) => {
            event.preventDefault();
            void send();
          }}
        >
          <Input
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="Message the assistant…"
            disabled={sending}
          />
          <Button type="submit" size="icon" disabled={sending || !input.trim()}>
            <Send className="size-4" />
          </Button>
        </form>
      </section>
    </div>
  );
}
