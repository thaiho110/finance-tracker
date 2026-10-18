import type { BudgetStatus } from '@finance-tracker/shared';

/** Bar/fill colour for a budget status: green under budget, amber near, red over (ADR-019). */
export const statusBar: Record<BudgetStatus, string> = {
  OK: 'bg-brand-primary',
  NEAR: 'bg-status-warning',
  OVER: 'bg-status-danger',
};

/** Text colour matching {@link statusBar}. */
export const statusText: Record<BudgetStatus, string> = {
  OK: 'text-brand-primary',
  NEAR: 'text-status-warning',
  OVER: 'text-status-danger',
};

/** Soft background for banners/badges. */
export const statusSoft: Record<BudgetStatus, string> = {
  OK: 'bg-brand-primary-soft',
  NEAR: 'bg-status-warning-soft',
  OVER: 'bg-status-danger-soft',
};
