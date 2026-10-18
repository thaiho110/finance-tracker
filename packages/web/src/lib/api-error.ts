/**
 * A readable message from an RTK Query error. Validation errors list the fields
 * ("amount: must be greater than 0") instead of just "Validation failed".
 */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const data = (err as { data?: { detail?: string; message?: string; errors?: string[] } } | undefined)?.data;
  if (data?.errors?.length) return `${data.detail ?? 'Validation failed'}: ${data.errors.join('; ')}`;
  return data?.detail || data?.message || fallback;
}
