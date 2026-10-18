/**
 * Reads the email (the JWT `sub` claim) from an access token WITHOUT verifying it.
 * For display only — e.g. when the server can't be reached on reload — never for authorization.
 */
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
