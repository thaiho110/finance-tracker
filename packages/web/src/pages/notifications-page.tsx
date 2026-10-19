import { useNavigate } from 'react-router-dom';
import { CheckCheck } from 'lucide-react';
import { cn } from 'cn';
import { Button } from '@/components/ui/button';
import {
  useNotificationsQuery,
  useMarkNotificationReadMutation,
  useMarkAllNotificationsReadMutation,
  type NotificationRow,
} from '@/store/api/notification-api';

export default function NotificationsPage() {
  const navigate = useNavigate();
  // SSE live-updates this via cache invalidation; polling + focus/reconnect refetch ensure the
  // feed stays current without a manual refresh even if the stream isn't connected.
  const { data: notifications = [] } = useNotificationsQuery(undefined, {
    pollingInterval: 15_000,
    refetchOnFocus: true,
    refetchOnReconnect: true,
  });
  const [markRead] = useMarkNotificationReadMutation();
  const [markAllRead] = useMarkAllNotificationsReadMutation();

  const open = (notification: NotificationRow) => {
    if (!notification.read) markRead(notification.id);
    const deepLink = notification.deepLink;
    if (deepLink) navigate(deepLink.startsWith('/meals') ? '/meals' : deepLink);
  };

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="page-title">Notifications</h1>
        {notifications.some((notification) => !notification.read) && (
          <Button variant="secondary" size="sm" className="gap-1" onClick={() => markAllRead()}>
            <CheckCheck className="size-4" /> Mark all read
          </Button>
        )}
      </div>

      <div className="divide-y divide-border-default rounded-[12px] border border-border-default bg-bg-surface">
        {notifications.length === 0 && (
          <p className="p-10 text-center text-sm text-text-muted">You're all caught up.</p>
        )}
        {notifications.map((notification) => (
          <button
            key={notification.id}
            onClick={() => open(notification)}
            className={cn(
              'flex w-full flex-col items-start gap-0.5 px-4 py-3 text-left hover:bg-bg-muted',
              !notification.read && 'bg-brand-primary-soft/30'
            )}
          >
            <div className="flex w-full items-center justify-between">
              <span className="text-sm font-medium text-text-default">{notification.title}</span>
              {!notification.read && <span className="mt-1 size-2 shrink-0 rounded-full bg-brand-primary" />}
            </div>
            {notification.body && <span className="text-xs text-text-muted">{notification.body}</span>}
            <span className="text-[11px] text-text-muted">{new Date(notification.createdAt).toLocaleString()}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
