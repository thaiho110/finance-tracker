import { describeProblem, type ProblemDetail } from '@finance-tracker/shared';

/**
 * A readable message from an RTK Query error. Validation errors list the fields
 * ("amount: must be greater than 0") instead of just "Validation failed".
 */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const data = (err as { data?: ProblemDetail & { message?: string } } | undefined)?.data;
  return describeProblem(data) || data?.message || fallback;
}
