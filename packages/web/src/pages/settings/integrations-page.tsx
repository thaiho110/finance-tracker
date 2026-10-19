import { useState } from 'react';
import { toast } from 'sonner';
import { Webhook, Trash2, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  useWebhookEndpointsQuery,
  useCreateWebhookEndpointMutation,
  useDeleteWebhookEndpointMutation,
  useWebhookSubscriptionsQuery,
  useCreateWebhookSubscriptionMutation,
  useDeleteWebhookSubscriptionMutation,
} from '@/store/api/integrations-api';

function Secret({ label, value }: { label: string; value: string }) {
  return (
    <button
      type="button"
      className="font-mono text-xs text-text-muted hover:text-text-default"
      title="Copy"
      onClick={() => { void navigator.clipboard.writeText(value); toast.success(`${label} copied`); }}
    >
      {label}: {value.slice(0, 10)}…
    </button>
  );
}

export default function IntegrationsPage() {
  const { data: endpoints = [] } = useWebhookEndpointsQuery();
  const { data: subscriptions = [] } = useWebhookSubscriptionsQuery();
  const [createEndpoint] = useCreateWebhookEndpointMutation();
  const [deleteEndpoint] = useDeleteWebhookEndpointMutation();
  const [createSubscription] = useCreateWebhookSubscriptionMutation();
  const [deleteSubscription] = useDeleteWebhookSubscriptionMutation();
  const [endpointName, setEndpointName] = useState('');
  const [targetUrl, setTargetUrl] = useState('');
  const [eventTypes, setEventTypes] = useState('integration.trigger');

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Webhook className="size-4" /> Inbound endpoints</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-text-muted">An external system POSTs to the endpoint URL with an HMAC signature to raise an event.</p>
          <div className="flex flex-wrap gap-2">
            <Input value={endpointName} onChange={(e) => setEndpointName(e.target.value)} placeholder="Endpoint name" className="max-w-56" />
            <Button className="gap-1" disabled={!endpointName.trim()} onClick={async () => { await createEndpoint({ name: endpointName.trim() }).unwrap(); setEndpointName(''); }}>
              <Plus className="size-4" /> Create
            </Button>
          </div>
          <div className="space-y-2">
            {endpoints.map((endpoint) => (
              <div key={endpoint.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border-default p-3">
                <div>
                  <p className="text-sm font-medium text-text-default">{endpoint.name}</p>
                  <div className="flex gap-3">
                    <Secret label="token" value={endpoint.token} />
                    <Secret label="secret" value={endpoint.secret} />
                  </div>
                </div>
                <Button variant="ghost" size="icon-sm" className="text-status-danger" onClick={() => deleteEndpoint(endpoint.id)}>
                  <Trash2 className="size-4" />
                </Button>
              </div>
            ))}
            {endpoints.length === 0 && <p className="text-sm text-text-muted">No endpoints yet.</p>}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Webhook className="size-4" /> Outbound subscriptions</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-text-muted">Kept POSTs a signed payload to your URL when a matching event fires.</p>
          <div className="flex flex-wrap gap-2">
            <Input value={targetUrl} onChange={(e) => setTargetUrl(e.target.value)} placeholder="https://example.com/hook" className="max-w-64" />
            <Input value={eventTypes} onChange={(e) => setEventTypes(e.target.value)} placeholder="event types (comma-separated)" className="max-w-56" />
            <Button
              className="gap-1"
              disabled={!targetUrl.trim()}
              onClick={async () => {
                await createSubscription({
                  targetUrl: targetUrl.trim(),
                  eventTypes: eventTypes.split(',').map((t) => t.trim()).filter(Boolean),
                }).unwrap();
                setTargetUrl('');
              }}
            >
              <Plus className="size-4" /> Add
            </Button>
          </div>
          <div className="space-y-2">
            {subscriptions.map((subscription) => (
              <div key={subscription.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border-default p-3">
                <div>
                  <p className="truncate text-sm font-medium text-text-default">{subscription.targetUrl}</p>
                  <p className="text-xs text-text-muted">{subscription.eventTypes.join(', ')}</p>
                  <Secret label="secret" value={subscription.secret} />
                </div>
                <Button variant="ghost" size="icon-sm" className="text-status-danger" onClick={() => deleteSubscription(subscription.id)}>
                  <Trash2 className="size-4" />
                </Button>
              </div>
            ))}
            {subscriptions.length === 0 && <p className="text-sm text-text-muted">No subscriptions yet.</p>}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
