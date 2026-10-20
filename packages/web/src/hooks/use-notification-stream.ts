import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { api } from '@/store/api-slice';
import { API_BASE_URL } from '@/lib/platform';
import { tokenStorage } from '@/lib/token-storage';
import { playChime } from '@/lib/chime';
import type { RootState } from '@/store';
import type { NotificationRow } from '@/store/api/notification-api';

/**
 * Opens a live SSE stream (ADR-021) while signed in: each incoming notification pops a toast, plays a chime, and
 * refreshes the feed/badge. This is how a meal call reaches other household members in real time.
 */
export function useNotificationStream() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const isAuthenticated = useSelector((state: RootState) => state.auth.isAuthenticated);

  useEffect(() => {
    if (!isAuthenticated) return;
    const token = tokenStorage.getToken();
    if (!token) return;

    const source = new EventSource(`${API_BASE_URL}/notifications/stream?token=${encodeURIComponent(token)}`);

    source.addEventListener('notification', (event) => {
      let payload: NotificationRow;
      try {
        payload = JSON.parse((event as MessageEvent).data) as NotificationRow;
      } catch {
        return;
      }
      playChime();
      const deepLink = payload.deepLink;
      const target = deepLink && deepLink.startsWith('/meals') ? '/meals' : deepLink;
      toast(payload.title, {
        description: payload.body ?? undefined,
        action: target ? { label: 'Open', onClick: () => navigate(target) } : undefined,
      });
      dispatch(api.util.invalidateTags(['Notification']));
    });

    source.onerror = () => {
      /* EventSource reconnects on its own; nothing to do */
    };

    return () => source.close();
  }, [isAuthenticated, dispatch, navigate]);
}
