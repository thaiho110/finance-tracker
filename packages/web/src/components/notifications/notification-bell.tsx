import { Bell } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useUnreadCountQuery } from '@/store/api/notification-api';

/** Bell + unread badge in the top bar; links to the notifications page (ADR-021). */
export function NotificationBell() {
  const { data } = useUnreadCountQuery(undefined, { pollingInterval: 30_000 });
  const count = data?.count ?? 0;

  return (
    <Link
      to="/notifications"
      className="relative flex size-8 items-center justify-center rounded-md text-text-muted hover:bg-bg-muted hover:text-text-default"
      title="Notifications"
    >
      <Bell className="size-4" />
      {count > 0 && (
        <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-status-danger px-1 text-[10px] font-medium text-white">
          {count > 9 ? '9+' : count}
        </span>
      )}
    </Link>
  );
}
