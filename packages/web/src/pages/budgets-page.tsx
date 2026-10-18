import { useState } from 'react';
import { toast } from 'sonner';
import type { BudgetRow } from '@finance-tracker/shared';
import { ApiError } from '@finance-tracker/shared';
import { useGetBudgetsQuery, useSetBudgetMutation } from '@/store/api/overview-api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { formatCurrency } from '@/lib/utils';
import { cn } from 'cn';

const rowKey = (row: BudgetRow) => row.categoryId ?? 'TOTAL';

export default function BudgetsPage() {
  const now = new Date();
  const [viewMonth, setViewMonth] = useState({ year: now.getFullYear(), month: now.getMonth() });
  const monthStr = `${viewMonth.year}-${String(viewMonth.month + 1).padStart(2, '0')}`;

  const { data: rows, isLoading } = useGetBudgetsQuery(monthStr);
  const [setBudget, { isLoading: isSaving }] = useSetBudgetMutation();
  const [drafts, setDrafts] = useState<Record<string, string>>({});

  const monthLabel = new Date(viewMonth.year, viewMonth.month).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  const isCurrentMonth = viewMonth.year === now.getFullYear() && viewMonth.month === now.getMonth();
  const prevMonth = () => setViewMonth((m) => (m.month === 0 ? { year: m.year - 1, month: 11 } : { ...m, month: m.month - 1 }));
  const nextMonth = () => setViewMonth((m) => (m.month === 11 ? { year: m.year + 1, month: 0 } : { ...m, month: m.month + 1 }));

  async function save(row: BudgetRow) {
    const key = rowKey(row);
    const raw = drafts[key];
    if (raw === undefined) return; // untouched
    const amount = raw.trim() === '' ? 0 : Number(raw.replace(/[^\d.-]/g, ''));
    if (Number.isNaN(amount) || amount < 0) {
      toast.error('Enter a budget of 0 or more');
      return;
    }
    if (amount === row.amount) {
      setDrafts((d) => { const next = { ...d }; delete next[key]; return next; });
      return;
    }
    try {
      await setBudget({ categoryId: row.categoryId ?? undefined, amount, month: monthStr }).unwrap();
      setDrafts((d) => { const next = { ...d }; delete next[key]; return next; });
      toast.success(amount === 0 ? `Budget removed for ${row.name}` : `Budget for ${row.name} set to ${formatCurrency(amount)}`);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Could not save the budget');
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <button onClick={prevMonth} className="rounded-[8px] p-1 text-text-muted hover:bg-bg-muted hover:text-text-default transition-colors" aria-label="Previous month">
          <ChevronLeft className="size-5" />
        </button>
        <h1 className="page-title">{monthLabel}</h1>
        <button
          onClick={nextMonth}
          disabled={isCurrentMonth}
          aria-label="Next month"
          className={cn('rounded-[8px] p-1 transition-colors', isCurrentMonth ? 'text-text-muted/30 cursor-not-allowed' : 'text-text-muted hover:bg-bg-muted hover:text-text-default')}
        >
          <ChevronRight className="size-5" />
        </button>
      </div>

      <p className="text-sm text-text-muted">
        A monthly budget for each spending category, in VND. Changes apply from {monthLabel} on; earlier months keep their own
        amounts. Set an amount to 0 to remove a budget.
      </p>

      <Card>
        <CardHeader>
          <CardTitle>Monthly budgets</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-3">
              {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-12 rounded-[8px]" />)}
            </div>
          ) : (
            <div className="divide-y divide-border-default">
              {/* Column header */}
              <div className="hidden grid-cols-[1fr_auto_auto] items-center gap-4 pb-2 sm:grid">
                <span className="field-label">Category</span>
                <span className="field-label w-40 text-right">This month / last</span>
                <span className="field-label w-40 text-right">Budget (VND)</span>
              </div>
              {(rows ?? []).map((row) => {
                const key = rowKey(row);
                const isTotal = row.categoryId === null;
                const value = drafts[key] ?? (row.amount === 0 ? '' : String(row.amount));
                return (
                  <div key={key} className={cn('grid grid-cols-1 gap-2 py-3 sm:grid-cols-[1fr_auto_auto] sm:items-center sm:gap-4', isTotal && 'bg-bg-muted/40 -mx-5 px-5')}>
                    <div className="flex items-center gap-2 min-w-0">
                      {!isTotal && row.color && <span className="size-2.5 rounded-full shrink-0" style={{ backgroundColor: row.color }} />}
                      <span className={cn('truncate text-sm text-text-default', isTotal && 'font-semibold')}>{isTotal ? 'Total (all spending)' : row.name}</span>
                    </div>
                    <div className="text-left text-xs text-text-muted sm:w-40 sm:text-right">
                      <span className="font-mono tabular-nums text-text-default">{formatCurrency(row.spent)}</span>
                      <span className="mx-1">/</span>
                      <span className="font-mono tabular-nums">{formatCurrency(row.previousSpent)}</span>
                    </div>
                    <div className="sm:w-40">
                      <Input
                        type="text"
                        inputMode="numeric"
                        value={value}
                        placeholder="No budget"
                        disabled={isSaving}
                        className="text-right font-mono tabular-nums"
                        onChange={(e) => setDrafts((d) => ({ ...d, [key]: e.target.value }))}
                        onBlur={() => save(row)}
                        onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur(); }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
