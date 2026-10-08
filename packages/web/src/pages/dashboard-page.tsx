import { useNavigate } from 'react-router-dom';
import { useGetTransactionsSummaryQuery } from '@/store/api/transaction-api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { ArrowUpRight, TrendingUp, TrendingDown, Wallet, Receipt, Upload } from 'lucide-react';
import {
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';

const COLORS = [
  'oklch(0.65 0.2 160)', 'oklch(0.7 0.15 250)', 'oklch(0.75 0.15 50)',
  'oklch(0.7 0.15 330)', 'oklch(0.6 0.15 190)', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316', '#84cc16',
];

function formatCurrency(n: number) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 }).format(Math.abs(n));
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const { data, isLoading } = useGetTransactionsSummaryQuery({});

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div>
          <Skeleton className="h-8 w-48 bg-muted/50" />
          <Skeleton className="mt-1 h-4 w-64 bg-muted/30" />
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i} className="border-border/50"><CardContent className="p-6"><Skeleton className="h-12 w-24 bg-muted/30" /></CardContent></Card>
          ))}
        </div>
        <div className="grid gap-6 lg:grid-cols-2">
          <Card className="border-border/50"><CardContent className="p-6"><Skeleton className="h-64 w-full bg-muted/30" /></CardContent></Card>
          <Card className="border-border/50"><CardContent className="p-6"><Skeleton className="h-64 w-full bg-muted/30" /></CardContent></Card>
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="flex flex-col items-center justify-center py-32">
        <div className="mb-6 flex h-20 w-20 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-500/20 to-emerald-600/10">
          <Wallet className="h-10 w-10 text-emerald-400" />
        </div>
        <h2 className="mb-2 text-xl font-semibold text-foreground">Welcome to Finance Tracker!</h2>
        <p className="mb-8 text-muted-foreground">Upload your first CSV to see your spending breakdown.</p>
        <Button
          onClick={() => navigate('/upload')}
          className="bg-gradient-to-r from-emerald-500 to-emerald-600 text-white shadow-lg shadow-emerald-500/20 hover:from-emerald-400 hover:to-emerald-500"
        >
          <Upload className="mr-2 h-4 w-4" />
          Upload CSV
        </Button>
      </div>
    );
  }

  const categoryData = (data.categoryBreakdown || []).map((c: any) => ({
    name: c.category,
    value: Math.abs(c.total),
  }));

  const monthlyData = (data.monthlyTrend || []).map((m: any) => ({
    name: m.month,
    amount: Math.abs(m.total),
  }));

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Dashboard</h1>
          <p className="text-muted-foreground">Your financial overview</p>
        </div>
        <Button
          onClick={() => navigate('/upload')}
          variant="outline"
          className="border-border/50 bg-card/50 text-muted-foreground hover:text-foreground"
        >
          <Upload className="mr-2 h-4 w-4" />
          Import
        </Button>
      </div>

      {/* Summary cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="card-hover border-border/50 bg-gradient-to-br from-card to-card/80">
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Total Expenses</CardTitle>
              <div className="rounded-lg bg-red-500/10 p-1.5">
                <TrendingDown className="h-3.5 w-3.5 text-red-400" />
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold tabular-nums text-red-400">{formatCurrency(data.totalExpenses || 0)}</p>
          </CardContent>
        </Card>
        <Card className="card-hover border-border/50 bg-gradient-to-br from-card to-card/80">
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Total Income</CardTitle>
              <div className="rounded-lg bg-emerald-500/10 p-1.5">
                <TrendingUp className="h-3.5 w-3.5 text-emerald-400" />
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold tabular-nums text-emerald-400">{formatCurrency(data.totalIncome || 0)}</p>
          </CardContent>
        </Card>
        <Card className="card-hover border-border/50 bg-gradient-to-br from-card to-card/80">
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Transactions</CardTitle>
              <div className="rounded-lg bg-blue-500/10 p-1.5">
                <Receipt className="h-3.5 w-3.5 text-blue-400" />
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold tabular-nums text-foreground">{data.transactionCount || 0}</p>
          </CardContent>
        </Card>
        <Card className="card-hover border-border/50 bg-gradient-to-br from-card to-card/80">
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Average</CardTitle>
              <div className="rounded-lg bg-purple-500/10 p-1.5">
                <ArrowUpRight className="h-3.5 w-3.5 text-purple-400" />
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold tabular-nums text-foreground">{formatCurrency(data.averageTransaction || 0)}</p>
          </CardContent>
        </Card>
      </div>

      {/* Charts */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Category breakdown */}
        <Card className="border-border/50">
          <CardHeader>
            <CardTitle className="text-sm font-semibold text-foreground">Spending by Category</CardTitle>
          </CardHeader>
          <CardContent>
            {categoryData.length > 0 ? (
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={categoryData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={100}
                    paddingAngle={3}
                    dataKey="value"
                  >
                    {categoryData.map((_: any, i: number) => (
                      <Cell key={i} fill={COLORS[i % COLORS.length]} stroke="none" />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(value: number) => [formatCurrency(value), 'Amount']}
                    contentStyle={{
                      background: 'oklch(0.18 0.02 240)',
                      border: '1px solid oklch(0.28 0.02 240)',
                      borderRadius: '8px',
                      color: 'oklch(0.95 0 0)',
                      fontSize: '13px',
                    }}
                  />
                  <Legend
                    formatter={(value: string) => <span style={{ color: 'oklch(0.6 0.02 240)', fontSize: '12px' }}>{value}</span>}
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <p className="py-12 text-center text-sm text-muted-foreground">No category data yet</p>
            )}
          </CardContent>
        </Card>

        {/* Monthly trend */}
        <Card className="border-border/50">
          <CardHeader>
            <CardTitle className="text-sm font-semibold text-foreground">Monthly Spending</CardTitle>
          </CardHeader>
          <CardContent>
            {monthlyData.length > 0 ? (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={monthlyData}>
                  <XAxis dataKey="name" tick={{ fontSize: 12, fill: 'oklch(0.6 0.02 240)' }} axisLine={{ stroke: 'oklch(0.28 0.02 240)' }} tickLine={false} />
                  <YAxis tick={{ fontSize: 12, fill: 'oklch(0.6 0.02 240)' }} tickFormatter={(v) => `$${v}`} axisLine={false} tickLine={false} />
                  <Tooltip
                    formatter={(value: number) => [formatCurrency(value), 'Spending']}
                    contentStyle={{
                      background: 'oklch(0.18 0.02 240)',
                      border: '1px solid oklch(0.28 0.02 240)',
                      borderRadius: '8px',
                      color: 'oklch(0.95 0 0)',
                      fontSize: '13px',
                    }}
                  />
                  <Bar dataKey="amount" fill="oklch(0.65 0.2 160)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <p className="py-12 text-center text-sm text-muted-foreground">No monthly data yet</p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Recent transactions */}
      {data.recentTransactions && data.recentTransactions.length > 0 && (
        <Card className="border-border/50">
          <CardHeader>
            <CardTitle className="text-sm font-semibold text-foreground">Recent Transactions</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-1">
              {data.recentTransactions.slice(0, 5).map((tx: any, i: number) => (
                <div
                  key={tx.id}
                  className="flex cursor-pointer items-center justify-between rounded-lg px-3 py-2.5 transition-all hover:bg-muted/50"
                  onClick={() => navigate(`/transactions/${tx.id}`)}
                  style={{ animationDelay: `${i * 50}ms` }}
                >
                  <div className="flex items-center gap-3">
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-muted">
                      <span className="text-xs font-medium text-muted-foreground">{tx.cleanMerchant?.charAt(0) || '?'}</span>
                    </div>
                    <div>
                      <p className="text-sm font-medium text-foreground">{tx.cleanMerchant}</p>
                      <p className="text-xs text-muted-foreground">{tx.date}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant="secondary" className="border-0 bg-muted text-xs font-normal text-muted-foreground">
                      {tx.category}
                    </Badge>
                    <span className={`text-sm font-semibold tabular-nums ${tx.amount < 0 ? 'text-red-400' : 'text-emerald-400'}`}>
                      {tx.amount < 0 ? '-' : '+'}{formatCurrency(tx.amount)}
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
