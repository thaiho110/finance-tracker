import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useGetTransactionsSummaryQuery } from '@/store/api/transaction-api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { StatCard } from '@/components/ui/stat-card';
import { CategoryBadge } from '@/components/ui/category-badge';
import { TrendingUp, Wallet, Upload, ChevronLeft, ChevronRight } from 'lucide-react';
import {
  PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer,
} from 'recharts';
import { formatCurrency, formatCurrencyFull, formatDateShort, CHART_COLORS, getCategoryTone, getChartColor } from '@/lib/utils';
import { cn } from 'cn';

export default function DashboardPage() {
  const navigate = useNavigate();
  const now = new Date();
  const [viewMonth, setViewMonth] = useState({ year: now.getFullYear(), month: now.getMonth() }); // 0-indexed

  const from = `${viewMonth.year}-${String(viewMonth.month + 1).padStart(2, '0')}-01`;
  const to = `${viewMonth.year}-${String(viewMonth.month + 1).padStart(2, '0')}-${new Date(viewMonth.year, viewMonth.month + 1, 0).getDate()}`;

  const { data, isLoading } = useGetTransactionsSummaryQuery({ from, to });

  const prevMonth = () => setViewMonth(m => m.month === 0 ? { year: m.year - 1, month: 11 } : { ...m, month: m.month - 1 });
  const nextMonth = () => {
    if (viewMonth.year === now.getFullYear() && viewMonth.month >= now.getMonth()) return;
    setViewMonth(m => m.month === 11 ? { year: m.year + 1, month: 0 } : { ...m, month: m.month + 1 });
  };

  const monthLabel = new Date(viewMonth.year, viewMonth.month).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

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

  if (!data || (data.transactionCount === 0 && !data.totalExpenses)) {
    return (
      <div className="flex flex-col items-center justify-center py-32">
        <div className="mb-6 flex h-20 w-20 items-center justify-center rounded-2xl bg-brand-primary-soft">
          <Wallet className="h-10 w-10 text-brand-primary" />
        </div>
        <h2 className="mb-2 text-[28px] font-semibold text-text-default">Welcome to Kept!</h2>
        <p className="mb-8 text-text-muted">Upload your first CSV to see your spending breakdown.</p>
        <Button onClick={() => navigate('/upload')}>
          <Upload className="size-4" />
          Upload CSV
        </Button>
      </div>
    );
  }

  // Spending only: income categories are in the totals above, transfers count nowhere (ADR-015).
  const categoryData = (data.categoryBreakdown || [])
    .filter((c: any) => c.kind === 'EXPENSE' && c.total > 0)
    .map((c: any, i: number) => ({
      id: c.categoryId,
      name: c.category,
      value: c.total,
      color: c.color || CHART_COLORS[i % CHART_COLORS.length],
    }));

  const monthlyData = (data.monthlyTrend || []).map((m: any) => ({
    name: m.month,
    amount: Math.abs(m.total),
  }));

  const totalMonth = categoryData.reduce((sum: number, c: any) => sum + c.value, 0);
  const isCurrentMonth = viewMonth.year === now.getFullYear() && viewMonth.month === now.getMonth();

  return (
    <div className="space-y-6">
      {/* Header — month switcher + Import button */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button onClick={prevMonth} className="rounded-[8px] p-1 text-text-muted hover:bg-bg-muted hover:text-text-default transition-colors">
            <ChevronLeft className="size-5" />
          </button>
          <h1 className="page-title">{monthLabel}</h1>
          <button
            onClick={nextMonth}
            disabled={isCurrentMonth}
            className={cn(
              'rounded-[8px] p-1 transition-colors',
              isCurrentMonth ? 'text-text-muted/30 cursor-not-allowed' : 'text-text-muted hover:bg-bg-muted hover:text-text-default'
            )}
          >
            <ChevronRight className="size-5" />
          </button>
        </div>
        <Button onClick={() => navigate('/upload')}>
          <Upload className="size-4" />
          Import CSV
        </Button>
      </div>

      {/* Summary cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="Total Expenses"
          value={data.totalExpenses || 0}
          delta={data.expenseDelta || undefined}
          deltaPositive={false}
          icon={<TrendingUp className="size-4" />}
        />
        <StatCard
          label="Transactions"
          value={data.transactionCount || 0}
          delta={data.countDelta || undefined}
          isText
        />
        <StatCard
          label="Avg Transaction"
          value={data.averageTransaction || 0}
          delta={data.avgDelta || undefined}
        />
        <StatCard
          label="Top Category"
          value={data.topCategory || '—'}
          delta={data.topCategoryDelta || undefined}
          isText
        />
      </div>

      {/* Charts */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Donut chart — Spending by Category */}
        <Card>
          <CardHeader>
            <CardTitle>Spending by Category</CardTitle>
          </CardHeader>
          <CardContent>
            {categoryData.length > 0 ? (
              <div className="relative">
                <ResponsiveContainer width="100%" height={300}>
                  <PieChart>
                    <Pie
                      data={categoryData}
                      cx="50%" cy="50%"
                      innerRadius={72} outerRadius={108}
                      paddingAngle={3}
                      dataKey="value"
                      onClick={(_, idx) => {
                        const cat = categoryData[idx]?.id;
                        if (cat) navigate(`/transactions?categoryId=${cat}`);
                      }}
                      style={{ cursor: 'pointer' }}
                    >
                      {categoryData.map((c: any) => (
                        <Cell key={c.id} fill={c.color} stroke="none" />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(value: number) => [formatCurrencyFull(value), 'Amount']}
                      contentStyle={{
                        background: 'var(--color-bg-surface)',
                        border: '1px solid var(--color-border-default)',
                        borderRadius: '8px',
                        color: 'var(--color-text-default)',
                        fontSize: '13px',
                      }}
                    />
                  </PieChart>
                </ResponsiveContainer>
                {/* Center text */}
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                  <span className="text-[11px] text-text-muted uppercase tracking-wider">Total</span>
                  <span className="text-[20px] font-mono font-semibold text-text-default">
                    {formatCurrency(totalMonth)}
                  </span>
                </div>
                {/* Legend */}
                <div className="flex flex-wrap gap-3 mt-2 justify-center">
                  {categoryData.map((c: any) => (
                    <button
                      key={c.id}
                      onClick={() => navigate(`/transactions?categoryId=${c.id}`)}
                      className="flex items-center gap-1.5 text-xs text-text-muted hover:text-text-default transition-colors"
                    >
                      <span className="size-2 rounded-full shrink-0" style={{ backgroundColor: c.color }} />
                      {c.name} {formatCurrency(c.value)}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <p className="py-12 text-center text-sm text-text-muted">No category data yet</p>
            )}
          </CardContent>
        </Card>

        {/* Bar chart — Monthly Spending */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Monthly Spending</CardTitle>
              {monthlyData.length >= 2 && (
                <div className="flex items-center gap-1.5 rounded-full bg-bg-muted px-2.5 py-0.5 text-xs font-medium text-text-muted">
                  <TrendingUp className="size-3" />
                  {(() => {
                    const curr = monthlyData[monthlyData.length - 1]?.amount || 0;
                    const prev = monthlyData[monthlyData.length - 2]?.amount || 1;
                    const pct = prev > 0 ? ((curr - prev) / prev * 100).toFixed(1) : '0';
                    return `${Number(pct) >= 0 ? '+' : ''}${pct}% vs previous`;
                  })()}
                </div>
              )}
            </div>
          </CardHeader>
          <CardContent>
            {monthlyData.length > 0 ? (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={monthlyData}>
                  <XAxis dataKey="name" tick={{ fontSize: 12, fill: 'var(--color-text-muted)' }} axisLine={{ stroke: 'var(--color-border-default)' }} tickLine={false} />
                  <YAxis tick={{ fontSize: 12, fill: 'var(--color-text-muted)' }} tickFormatter={(v) => formatCurrency(v)} axisLine={false} tickLine={false} />
                  <Tooltip
                    formatter={(value: number) => [formatCurrencyFull(value), 'Spending']}
                    contentStyle={{
                      background: 'var(--color-bg-surface)',
                      border: '1px solid var(--color-border-default)',
                      borderRadius: '8px',
                      color: 'var(--color-text-default)',
                      fontSize: '13px',
                    }}
                  />
                  <Bar dataKey="amount" radius={[4, 4, 0, 0]}>
                    {monthlyData.map((_: unknown, i: number) => (
                      <Cell
                        key={i}
                        fill={i === monthlyData.length - 1 ? 'var(--color-brand-primary)' : 'var(--color-brand-primary-soft)'}
                      />
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

      {/* Recent transactions */}
      {data.recentTransactions && data.recentTransactions.length > 0 && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Recent Transactions</CardTitle>
              <Button variant="ghost" size="sm" onClick={() => navigate('/transactions')}>
                View all
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <div className="divide-y divide-border-default">
              {data.recentTransactions.slice(0, 5).map((tx: any) => (
                <div
                  key={tx.id}
                  className="flex items-center justify-between py-3 cursor-pointer hover:bg-bg-canvas -mx-5 px-5 transition-colors"
                  onClick={() => navigate(`/transactions/${tx.id}`)}
                >
                  <div className="flex items-center gap-3">
                    <div className="flex size-9 items-center justify-center rounded-full bg-bg-muted">
                      <span className="text-xs font-medium text-text-muted">
                        {tx.cleanMerchant?.charAt(0) || '?'}
                      </span>
                    </div>
                    <div>
                      <p className="text-sm font-medium text-text-default">{tx.cleanMerchant}</p>
                      <p className="text-xs text-text-muted">{formatDateShort(tx.date)}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <CategoryBadge category={tx.category} color={tx.categoryColor} />
                    <span className="text-sm font-semibold text-mono tabular-nums text-right text-text-default min-w-[80px]">
                      {formatCurrencyFull(tx.amount)}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
