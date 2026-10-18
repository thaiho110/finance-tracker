import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Overview, OverviewCategory } from '@finance-tracker/shared';
import { useGetOverviewQuery } from '@/store/api/overview-api';
import { useGetTransactionsSummaryQuery } from '@/store/api/transaction-api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { StatCard } from '@/components/ui/stat-card';
import { TrendingUp, TrendingDown, Wallet, Upload, ChevronLeft, ChevronRight, AlertTriangle } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import { formatCurrency, formatCurrencyFull, CHART_COLORS } from '@/lib/utils';
import { statusBar, statusText, statusSoft } from '@/lib/budget-status';
import { cn } from 'cn';

/** A percentage-change caption vs last month, or undefined when there is nothing to compare to. */
function deltaLabel(current: number, previous: number): string | undefined {
  if (previous === 0) return current === 0 ? undefined : 'new vs last month';
  const pct = ((current - previous) / Math.abs(previous)) * 100;
  return `${pct >= 0 ? '+' : ''}${pct.toFixed(0)}% vs last month`;
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const now = new Date();
  const [viewMonth, setViewMonth] = useState({ year: now.getFullYear(), month: now.getMonth() }); // 0-indexed

  const monthStr = `${viewMonth.year}-${String(viewMonth.month + 1).padStart(2, '0')}`;
  const { data: overview, isLoading } = useGetOverviewQuery(monthStr);
  // The all-time monthly trend chart stays on the summary endpoint.
  const { data: summary } = useGetTransactionsSummaryQuery({});

  const prevMonth = () => setViewMonth((m) => (m.month === 0 ? { year: m.year - 1, month: 11 } : { ...m, month: m.month - 1 }));
  const nextMonth = () => {
    if (viewMonth.year === now.getFullYear() && viewMonth.month >= now.getMonth()) return;
    setViewMonth((m) => (m.month === 11 ? { year: m.year + 1, month: 0 } : { ...m, month: m.month + 1 }));
  };

  const monthLabel = new Date(viewMonth.year, viewMonth.month).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  const isCurrentMonth = viewMonth.year === now.getFullYear() && viewMonth.month === now.getMonth();

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-9 w-28" />
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-32 rounded-[12px]" />)}
        </div>
        <div className="grid gap-6 lg:grid-cols-2">
          <Skeleton className="h-80 rounded-[12px]" />
          <Skeleton className="h-80 rounded-[12px]" />
        </div>
      </div>
    );
  }

  const empty = !overview || (overview.income === 0 && overview.expenses === 0 && overview.categories.length === 0);
  if (empty) {
    return (
      <div className="space-y-6">
        <MonthSwitcher label={monthLabel} isCurrentMonth={isCurrentMonth} onPrev={prevMonth} onNext={nextMonth} onImport={() => navigate('/upload')} />
        <div className="flex flex-col items-center justify-center py-28">
          <div className="mb-6 flex h-20 w-20 items-center justify-center rounded-2xl bg-brand-primary-soft">
            <Wallet className="h-10 w-10 text-brand-primary" />
          </div>
          <h2 className="mb-2 text-[28px] font-semibold text-text-default">Nothing in {monthLabel}</h2>
          <p className="mb-8 text-text-muted">No income or spending recorded for this month yet.</p>
          <Button onClick={() => navigate('/upload')}>
            <Upload className="size-4" />
            Import CSV
          </Button>
        </div>
      </div>
    );
  }

  const ov = overview as Overview;
  const monthlyData = ((summary?.monthlyTrend ?? []) as { month: string; total: number }[]).map((m) => ({ name: m.month, amount: Math.abs(m.total) }));

  return (
    <div className="space-y-6">
      <MonthSwitcher label={monthLabel} isCurrentMonth={isCurrentMonth} onPrev={prevMonth} onNext={nextMonth} onImport={() => navigate('/upload')} />

      <BudgetBanner overview={ov} />

      {/* Summary cards — this month, compared with last month */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Income" value={ov.income} delta={deltaLabel(ov.income, ov.previousIncome)} deltaPositive icon={<TrendingUp className="size-4" />} />
        <StatCard label="Expenses" value={ov.expenses} delta={deltaLabel(ov.expenses, ov.previousExpenses)} deltaPositive={false} icon={<TrendingDown className="size-4" />} />
        <StatCard label="Net" value={ov.net} delta={deltaLabel(ov.net, ov.previousIncome - ov.previousExpenses)} deltaPositive={ov.net >= 0} />
        {ov.budgetsEnabled && ov.totalBudget ? (
          <StatCard
            label="Total budget"
            value={`${formatCurrency(ov.totalBudget.spent)} / ${formatCurrency(ov.totalBudget.amount)}`}
            delta={ov.totalBudget.status === 'OVER' ? 'Over budget' : ov.totalBudget.status === 'NEAR' ? 'Close to budget' : 'On track'}
            isText
          />
        ) : (
          <StatCard label="Top merchant" value={ov.topMerchants[0]?.merchant ?? '—'} delta={ov.topMerchants[0] ? formatCurrency(ov.topMerchants[0].total) : undefined} isText />
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Spending by category — progress bars vs budget and vs last month */}
        <Card>
          <CardHeader>
            <CardTitle>Spending by category</CardTitle>
          </CardHeader>
          <CardContent>
            {ov.categories.length > 0 ? (
              <div className="space-y-4">
                {ov.categories.map((c, i) => (
                  <CategoryRow key={c.categoryId} category={c} color={c.color || CHART_COLORS[i % CHART_COLORS.length]} onClick={() => navigate(`/transactions?categoryId=${c.categoryId}`)} />
                ))}
              </div>
            ) : (
              <p className="py-12 text-center text-sm text-text-muted">No spending this month</p>
            )}
          </CardContent>
        </Card>

        {/* Monthly spending trend (all-time, from the summary endpoint) */}
        <Card>
          <CardHeader>
            <CardTitle>Monthly spending</CardTitle>
          </CardHeader>
          <CardContent>
            {monthlyData.length > 0 ? (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={monthlyData}>
                  <XAxis dataKey="name" tick={{ fontSize: 12, fill: 'var(--color-text-muted)' }} axisLine={{ stroke: 'var(--color-border-default)' }} tickLine={false} />
                  <YAxis tick={{ fontSize: 12, fill: 'var(--color-text-muted)' }} tickFormatter={(v) => formatCurrency(v)} axisLine={false} tickLine={false} />
                  <Tooltip
                    formatter={(value: number) => [formatCurrencyFull(value), 'Spending']}
                    contentStyle={{ background: 'var(--color-bg-surface)', border: '1px solid var(--color-border-default)', borderRadius: '8px', color: 'var(--color-text-default)', fontSize: '13px' }}
                  />
                  <Bar dataKey="amount" radius={[4, 4, 0, 0]}>
                    {monthlyData.map((_: unknown, i: number) => (
                      <Cell key={i} fill={i === monthlyData.length - 1 ? 'var(--color-brand-primary)' : 'var(--color-brand-primary-soft)'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <p className="py-12 text-center text-sm text-text-muted">No monthly data yet</p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Top merchants */}
      {ov.topMerchants.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Top merchants</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="divide-y divide-border-default">
              {ov.topMerchants.map((m) => (
                <div key={m.merchant} className="flex items-center justify-between py-3">
                  <div className="flex items-center gap-3">
                    <div className="flex size-9 items-center justify-center rounded-full bg-bg-muted">
                      <span className="text-xs font-medium text-text-muted">{m.merchant?.charAt(0) || '?'}</span>
                    </div>
                    <div>
                      <p className="text-sm font-medium text-text-default">{m.merchant}</p>
                      <p className="text-xs text-text-muted">{m.count} transaction{m.count === 1 ? '' : 's'}</p>
                    </div>
                  </div>
                  <span className="text-sm font-semibold font-mono tabular-nums text-text-default">{formatCurrencyFull(m.total)}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function MonthSwitcher({ label, isCurrentMonth, onPrev, onNext, onImport }: { label: string; isCurrentMonth: boolean; onPrev: () => void; onNext: () => void; onImport: () => void }) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-3">
        <button onClick={onPrev} className="rounded-[8px] p-1 text-text-muted hover:bg-bg-muted hover:text-text-default transition-colors" aria-label="Previous month">
          <ChevronLeft className="size-5" />
        </button>
        <h1 className="page-title">{label}</h1>
        <button
          onClick={onNext}
          disabled={isCurrentMonth}
          aria-label="Next month"
          className={cn('rounded-[8px] p-1 transition-colors', isCurrentMonth ? 'text-text-muted/30 cursor-not-allowed' : 'text-text-muted hover:bg-bg-muted hover:text-text-default')}
        >
          <ChevronRight className="size-5" />
        </button>
      </div>
      <Button onClick={onImport}>
        <Upload className="size-4" />
        Import CSV
      </Button>
    </div>
  );
}

function BudgetBanner({ overview }: { overview: Overview }) {
  if (!overview.budgetsEnabled) return null;
  const over = overview.categories.filter((c) => c.status === 'OVER');
  const near = overview.categories.filter((c) => c.status === 'NEAR');
  const totalOver = overview.totalBudget?.status === 'OVER';
  const totalNear = overview.totalBudget?.status === 'NEAR';
  if (over.length === 0 && near.length === 0 && !totalOver && !totalNear) return null;

  const level = over.length > 0 || totalOver ? 'OVER' : 'NEAR';
  const parts: string[] = [];
  if (totalOver) parts.push('your total budget is over');
  else if (totalNear) parts.push('your total budget is close');
  if (over.length > 0) parts.push(`${over.length} ${over.length === 1 ? 'category is' : 'categories are'} over budget`);
  if (near.length > 0) parts.push(`${near.length} ${near.length === 1 ? 'category is' : 'categories are'} close`);

  return (
    <div className={cn('flex items-start gap-3 rounded-[12px] border border-border-default p-4', statusSoft[level])}>
      <AlertTriangle className={cn('mt-0.5 size-5 shrink-0', statusText[level])} />
      <p className="text-sm text-text-default">
        This month {parts.join(', ')}.
      </p>
    </div>
  );
}

function CategoryRow({ category, color, onClick }: { category: OverviewCategory; color: string; onClick: () => void }) {
  const pct = category.budget && category.budget > 0 ? Math.min(100, Math.max(0, (category.spent / category.budget) * 100)) : null;
  const vsLast = deltaLabel(category.spent, category.previousSpent);
  return (
    <button onClick={onClick} className="block w-full text-left">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="size-2.5 rounded-full shrink-0" style={{ backgroundColor: color }} />
          <span className="text-sm font-medium text-text-default truncate">{category.name}</span>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span className={cn('text-sm font-mono tabular-nums', category.status ? statusText[category.status] : 'text-text-default')}>
            {formatCurrency(category.spent)}
          </span>
          {category.budget != null && category.budget > 0 && (
            <span className="text-xs text-text-muted">/ {formatCurrency(category.budget)}</span>
          )}
        </div>
      </div>
      <div className="mt-1.5 flex items-center gap-2">
        {pct != null ? (
          <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-bg-muted">
            <div className={cn('h-full rounded-full transition-all', category.status ? statusBar[category.status] : 'bg-brand-primary')} style={{ width: `${pct}%` }} />
          </div>
        ) : (
          <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-bg-muted">
            <div className="h-full rounded-full" style={{ width: '100%', backgroundColor: color, opacity: 0.35 }} />
          </div>
        )}
        {vsLast && <span className="text-[11px] text-text-muted shrink-0">{vsLast}</span>}
      </div>
    </button>
  );
}
