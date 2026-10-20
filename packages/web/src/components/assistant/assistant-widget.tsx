import { useState } from 'react';
import { MessageSquare, X, Send, Loader2, Sparkles } from 'lucide-react';
import { cn } from 'cn';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useCan } from '@/lib/permissions';
import {
  useThreadsQuery,
  useCreateThreadMutation,
  useThreadMessagesQuery,
  usePostAssistantMessageMutation,
} from '@/store/api/assistant-api';

/** Floating assistant bubble, available on every page (ADR-024). Replaces the dedicated chat page. */
export function AssistantWidget() {
  const can = useCan();
  const [open, setOpen] = useState(false);

  if (!can('ASSISTANT_USE')) return null;

  return (
    <>
      {!open && (
        <button
          onClick={() => setOpen(true)}
          className="fixed bottom-6 right-6 z-50 flex size-14 items-center justify-center rounded-full bg-brand-primary text-white shadow-lg transition-transform hover:scale-105"
          aria-label="Open assistant"
        >
          <MessageSquare className="size-6" />
        </button>
      )}
      {open && <AssistantPanel onClose={() => setOpen(false)} />}
    </>
  );
}

function AssistantPanel({ onClose }: { onClose: () => void }) {
  const { data: threads = [] } = useThreadsQuery();
  const [createThread] = useCreateThreadMutation();
  const [postMessage, { isLoading: sending }] = usePostAssistantMessageMutation();
  const [activeId, setActiveId] = useState<string | null>(null);
  const [input, setInput] = useState('');

  const currentId = activeId ?? threads[0]?.id ?? null;
  const { data: messages = [] } = useThreadMessagesQuery(currentId as string, { skip: !currentId });

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
    <div className="fixed bottom-6 right-6 z-50 flex h-[32rem] w-[22rem] max-w-[calc(100vw-2rem)] flex-col rounded-[16px] border border-border-default bg-bg-surface shadow-2xl">
      <header className="flex items-center justify-between border-b border-border-default px-4 py-3">
        <div className="flex items-center gap-2">
          <Sparkles className="size-4 text-brand-primary" />
          <span className="text-sm font-semibold text-text-default">Assistant</span>
        </div>
        <button onClick={onClose} className="text-text-muted hover:text-text-default" aria-label="Close assistant">
          <X className="size-4" />
        </button>
      </header>

      <div className="flex-1 space-y-3 overflow-y-auto p-4">
        {messages.length === 0 && (
          <p className="mt-10 text-center text-sm text-text-muted">
            Ask me to do things — add a transaction, call the household to dinner, or answer a question.
          </p>
        )}
        {messages.map((message) => (
          <div key={message.id} className={cn('flex', message.role === 'USER' ? 'justify-end' : 'justify-start')}>
            <div
              className={cn(
                'max-w-[85%] whitespace-pre-wrap rounded-2xl px-3 py-2 text-sm',
                message.role === 'USER' ? 'bg-brand-primary text-white' : 'bg-bg-muted text-text-default'
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
        onSubmit={(event) => { event.preventDefault(); void send(); }}
      >
        <Input value={input} onChange={(event) => setInput(event.target.value)} placeholder="Message the assistant…" disabled={sending} />
        <Button type="submit" size="icon" disabled={sending || !input.trim()}>
          <Send className="size-4" />
        </Button>
      </form>
    </div>
  );
}
