/** ₫1,240,000 — amounts are shown without sign; the context says whether it's spent or received. */
export function formatVnd(amount: number | null | undefined): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) return '—';
  return `₫${new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(Math.abs(amount))}`;
}

/** Reads the email (the JWT `sub` claim) from a token WITHOUT verifying it — for display only. */
export function emailFromToken(token: string | null): string | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='));
    const sub: unknown = JSON.parse(json).sub;
    return typeof sub === 'string' ? sub : null;
  } catch {
    return null;
  }
}

/** Today in the device's time zone as yyyy-mm-dd. */
export function todayIso(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}
