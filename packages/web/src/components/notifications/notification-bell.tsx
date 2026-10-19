import { Bell, CheckCheck } from 'lucide-react';
import { cn } from 'cn';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  useNotificationsQuery,
  useUnreadCountQuery,
  useMarkNotificationReadMutation,
  useMarkAllNotificationsReadMutation,
} from '@/store/api/notification-api';

/** Bell + unread badge in the top bar, with a dropdown feed (ADR-021). */
export function NotificationBell() {
  const { data: unread } = useUnreadCountQuery(undefined, { pollingInterval: 30_000 });
  const { data: notifications = [] } = useNotificationsQuery();
  const [markRead] = useMarkNotificationReadMutation();
  const [markAllRead] = useMarkAllNotificationsReadMutation();
  const count = unread?.count ?? 0;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon-sm" className="relative text-text-muted hover:text-text-default" title="Notifications">
          <Bell className="size-4" />
          {count > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-status-danger px-1 text-[10px] font-medium text-white">
              {count > 9 ? '9+' : count}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80 p-0">
        <div className="flex items-center justify-between border-b border-border-default px-3 py-2">
          <span className="text-sm font-medium text-text-default">Notifications</span>
          {count > 0 && (
            <button className="flex items-center gap-1 text-xs text-text-muted hover:text-text-default" onClick={() => markAllRead()}>
              <CheckCheck className="size-3.5" /> Mark all read
            </button>
          )}
        </div>
        <div className="max-h-80 overflow-y-auto">
          {notifications.length === 0 && <p className="px-3 py-6 text-center text-sm text-text-muted">You're all caught up.</p>}
          {notifications.map((notification) => (
            <button
              key={notification.id}
              onClick={() => { if (!notification.read) markRead(notification.id); }}
              className={cn(
                'flex w-full flex-col items-start gap-0.5 border-b border-border-default px-3 py-2 text-left last:border-0 hover:bg-bg-muted',
                !notification.read && 'bg-brand-primary-soft/40'
              )}
            >
              <span className="text-sm font-medium text-text-default">{notification.title}</span>
              {notification.body && <span className="text-xs text-text-muted">{notification.body}</span>}
              <span className="text-[10px] text-text-muted">{new Date(notification.createdAt).toLocaleString()}</span>
            </button>
          ))}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
