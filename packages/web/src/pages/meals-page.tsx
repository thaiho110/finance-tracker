import { useState } from 'react';
import { toast } from 'sonner';
import { UtensilsCrossed, Users, LogOut, Copy, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useCan } from '@/lib/permissions';
import {
  useHouseholdQuery,
  useCreateHouseholdMutation,
  useInviteToHouseholdMutation,
  useJoinHouseholdMutation,
  useLeaveHouseholdMutation,
} from '@/store/api/household-api';
import {
  useMealCallsQuery,
  useCallMealMutation,
  useRespondToMealMutation,
  type MealResponse,
} from '@/store/api/meals-api';

const RESPONSES: MealResponse[] = ['COMING', 'LATER', 'SKIP'];
const RESPONSE_LABEL: Record<MealResponse, string> = { COMING: 'Coming', LATER: 'Later', SKIP: 'Skip' };

function HouseholdCard() {
  const { data: household, isLoading } = useHouseholdQuery();
  const [createHousehold, { isLoading: creating }] = useCreateHouseholdMutation();
  const [invite, { isLoading: inviting }] = useInviteToHouseholdMutation();
  const [join, { isLoading: joining }] = useJoinHouseholdMutation();
  const [leave] = useLeaveHouseholdMutation();
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [email, setEmail] = useState('');

  if (isLoading) return <Loader2 className="size-5 animate-spin text-text-muted" />;

  if (!household) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Users className="size-4" /> Your household</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-text-muted">Create a household, or join one with an invite code, to use meal calls.</p>
          <div className="flex flex-wrap gap-2">
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Household name" className="max-w-48" />
            <Button disabled={creating || !name.trim()} onClick={() => createHousehold({ name: name.trim() })}>Create</Button>
          </div>
          <div className="flex flex-wrap gap-2">
            <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Invite code" className="max-w-48" />
            <Button variant="secondary" disabled={joining || !code.trim()} onClick={() => join({ code: code.trim() })}>Join</Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  const sendInvite = async () => {
    const result = await invite({ email: email.trim() }).unwrap();
    setEmail('');
    try {
      await navigator.clipboard.writeText(result.code);
      toast.success('Invite code copied', { description: result.code });
    } catch {
      toast.success('Invite code created', { description: result.code });
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Users className="size-4" /> {household.name}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <ul className="space-y-1 text-sm">
          {household.members.map((member) => (
            <li key={member.userId} className="text-text-default">{member.email ?? member.userId}</li>
          ))}
        </ul>
        <div className="flex flex-wrap items-center gap-2">
          <Input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Invite by email" className="max-w-56" />
          <Button variant="secondary" size="sm" className="gap-1" disabled={inviting || !email.trim()} onClick={sendInvite}>
            <Copy className="size-4" /> Create invite
          </Button>
          <Button variant="ghost" size="sm" className="gap-1 text-status-danger" onClick={() => leave()}>
            <LogOut className="size-4" /> Leave
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

export default function MealsPage() {
  const can = useCan();
  const { data: household } = useHouseholdQuery();
  const { data: calls = [] } = useMealCallsQuery(undefined, { skip: !household });
  const [callMeal, { isLoading: calling }] = useCallMealMutation();
  const [respond] = useRespondToMealMutation();
  const [message, setMessage] = useState('');

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <HouseholdCard />

      {household && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><UtensilsCrossed className="size-4" /> Call to the table</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {can('MEALS_CALL') && (
              <div className="flex flex-wrap gap-2">
                <Input value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Come to the table" />
                <Button
                  disabled={calling}
                  onClick={async () => { await callMeal({ message: message.trim() || undefined }).unwrap(); setMessage(''); }}
                >
                  Call
                </Button>
              </div>
            )}

            <div className="space-y-3">
              {calls.length === 0 && <p className="text-sm text-text-muted">No meal calls yet.</p>}
              {calls.map((call) => (
                <div key={call.id} className="rounded-lg border border-border-default p-3">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-medium text-text-default">{call.message}</p>
                    <span className="text-xs text-text-muted">{new Date(call.createdAt).toLocaleString()}</span>
                  </div>
                  {call.responses.length > 0 && (
                    <p className="mt-1 text-xs text-text-muted">
                      {call.responses.map((ack) => `${ack.userId.slice(0, 8)}: ${RESPONSE_LABEL[ack.response]}`).join(' · ')}
                    </p>
                  )}
                  {can('MEALS_RESPOND') && (
                    <div className="mt-2 flex gap-2">
                      {RESPONSES.map((response) => (
                        <Button
                          key={response}
                          variant="secondary"
                          size="sm"
                          onClick={() => respond({ id: call.id, response })}
                        >
                          {RESPONSE_LABEL[response]}
                        </Button>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
