import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowUpDown,
  Search,
  Trash2,
  Pencil,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Filter,
  X,
  Upload,
} from 'lucide-react';
import { useGetTransactionsQuery, useDeleteTransactionMutation } from '@/store/api/transaction-api';
import { useGetCategoriesQuery } from '@/store/api/category-api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { cn } from 'cn';

function formatCurrency(n: number) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 }).format(Math.abs(n));
}

export default function TransactionsPage() {
  const [page, setPage] = useState(0);
  const [size] = useState(25);
  const [category, setCategory] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState('date,desc');
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const { data, isLoading, isFetching } = useGetTransactionsQuery({
    page,
    size,
    category: category || undefined,
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    sort,
  });
  const { data: categories = [] } = useGetCategoriesQuery();
  const [deleteTransaction] = useDeleteTransactionMutation();
  const navigate = useNavigate();

  const transactions = data?.content || [];
  const totalPages = data?.totalPages || 0;
  const totalElements = data?.totalElements || 0;

  const handleDelete = async () => {
    if (!deleteId) return;
    try {
      await deleteTransaction(deleteId).unwrap();
      setDeleteId(null);
    } catch {}
  };

  const clearFilters = () => {
    setCategory('');
    setDateFrom('');
    setDateTo('');
    setSearch('');
    setPage(0);
  };

  const hasFilters = category || dateFrom || dateTo || search;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Transactions</h1>
          <p className="text-muted-foreground">
            <span className="tabular-nums">{totalElements}</span> transaction{totalElements !== 1 ? 's' : ''}
          </p>
        </div>
        <Button
          onClick={() => navigate('/upload')}
          className="bg-gradient-to-r from-emerald-500 to-emerald-600 text-white shadow-lg shadow-emerald-500/20 hover:from-emerald-400 hover:to-emerald-500"
        >
          <Upload className="mr-2 h-4 w-4" />
          Import CSV
        </Button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-border/50 bg-card/50 p-4">
        <div className="space-y-1.5">
          <label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Category</label>
          <Select value={category} onValueChange={(v) => { setCategory(v); setPage(0); }}>
            <SelectTrigger className="h-9 w-40 border-border/50 bg-background/50 text-foreground">
              <SelectValue placeholder="All categories" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="">All categories</SelectItem>
              {categories.map((c) => (
                <SelectItem key={c} value={c}>{c}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1.5">
          <label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">From</label>
          <Input
            type="date"
            value={dateFrom}
            onChange={(e) => { setDateFrom(e.target.value); setPage(0); }}
            className="h-9 w-36 border-border/50 bg-background/50 text-foreground"
          />
        </div>
        <div className="space-y-1.5">
          <label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">To</label>
          <Input
            type="date"
            value={dateTo}
            onChange={(e) => { setDateTo(e.target.value); setPage(0); }}
            className="h-9 w-36 border-border/50 bg-background/50 text-foreground"
          />
        </div>
        <div className="space-y-1.5">
          <label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Search</label>
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Merchant or description..."
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(0); }}
              className="h-9 w-56 border-border/50 bg-background/50 pl-8 text-foreground placeholder:text-muted-foreground/50"
            />
          </div>
        </div>
        {hasFilters && (
          <Button variant="ghost" size="sm" onClick={clearFilters} className="h-9 text-muted-foreground hover:text-foreground">
            <X className="mr-1 h-3.5 w-3.5" />
            Clear
          </Button>
        )}
      </div>

      {/* Table */}
      <div className="rounded-xl border border-border/50 overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow className="border-border/50 bg-muted/30 hover:bg-muted/30">
              <TableHead className="h-10 w-28">
                <button onClick={() => setSort(sort === 'date,asc' ? 'date,desc' : 'date,asc')} className="flex items-center gap-1 text-[11px] font-medium uppercase tracking-wider text-muted-foreground hover:text-foreground transition-colors">
                  Date <ArrowUpDown className="h-3 w-3" />
                </button>
              </TableHead>
              <TableHead className="h-10 w-44">
                <button onClick={() => setSort(sort === 'cleanMerchant,asc' ? 'cleanMerchant,desc' : 'cleanMerchant,asc')} className="flex items-center gap-1 text-[11px] font-medium uppercase tracking-wider text-muted-foreground hover:text-foreground transition-colors">
                  Merchant <ArrowUpDown className="h-3 w-3" />
                </button>
              </TableHead>
              <TableHead className="hidden h-10 md:table-cell text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Description</TableHead>
              <TableHead className="h-10 w-28 text-right">
                <button onClick={() => setSort(sort === 'amount,asc' ? 'amount,desc' : 'amount,asc')} className="flex items-center gap-1 justify-end text-[11px] font-medium uppercase tracking-wider text-muted-foreground hover:text-foreground transition-colors w-full">
                  Amount <ArrowUpDown className="h-3 w-3" />
                </button>
              </TableHead>
              <TableHead className="h-10 w-32 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Category</TableHead>
              <TableHead className="h-10 w-16 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Src</TableHead>
              <TableHead className="h-10 w-12" />
              <TableHead className="h-10 w-20" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <TableRow key={i} className="border-border/50">
                  {Array.from({ length: 8 }).map((_, j) => (
                    <TableCell key={j}><Skeleton className="h-5 w-full bg-muted/30" /></TableCell>
                  ))}
                </TableRow>
              ))
            ) : transactions.length === 0 ? (
              <TableRow className="border-border/50">
                <TableCell colSpan={8} className="py-16 text-center text-muted-foreground">
                  {hasFilters ? (
                    <div className="space-y-3">
                      <Filter className="mx-auto h-8 w-8 text-muted-foreground/50" />
                      <p>No transactions match your filters.</p>
                      <Button variant="outline" size="sm" onClick={clearFilters} className="border-border/50">Clear filters</Button>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      <Upload className="mx-auto h-8 w-8 text-muted-foreground/50" />
                      <p>No transactions yet.</p>
                      <Button variant="outline" size="sm" onClick={() => navigate('/upload')} className="border-border/50">
                        Upload a CSV to get started
                      </Button>
                    </div>
                  )}
                </TableCell>
              </TableRow>
            ) : (
              transactions.map((tx: any) => (
                <TableRow
                  key={tx.id}
                  className={cn(
                    'cursor-pointer border-border/50 transition-colors hover:bg-muted/20',
                    tx.isDuplicate && 'bg-yellow-500/5'
                  )}
                  onClick={() => navigate(`/transactions/${tx.id}`)}
                >
                  <TableCell className="py-3 text-sm text-muted-foreground tabular-nums">{tx.date}</TableCell>
                  <TableCell className="py-3 font-medium text-foreground">{tx.cleanMerchant}</TableCell>
                  <TableCell className="hidden max-w-48 truncate py-3 text-sm text-muted-foreground md:table-cell" title={tx.rawDescription}>
                    {tx.rawDescription}
                  </TableCell>
                  <TableCell className={`py-3 text-right font-semibold tabular-nums ${tx.amount < 0 ? 'text-red-400' : 'text-emerald-400'}`}>
                    {tx.amount < 0 ? '-' : '+'}{formatCurrency(tx.amount)}
                  </TableCell>
                  <TableCell className="py-3">
                    <Badge variant="secondary" className="border-0 bg-muted text-xs font-normal text-muted-foreground">
                      {tx.category}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3">
                    <Badge variant="outline" className="border-border/50 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
                      {tx.sourceType}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3">
                    {tx.isDuplicate && <AlertCircle className="h-4 w-4 text-yellow-500" title="Duplicate" />}
                  </TableCell>
                  <TableCell className="py-3">
                    <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                      <Button variant="ghost" size="icon" className="h-7 w-7 text-muted-foreground hover:text-foreground" onClick={() => navigate(`/transactions/${tx.id}`)}>
                        <Pencil className="h-3 w-3" />
                      </Button>
                      <Button variant="ghost" size="icon" className="h-7 w-7 text-muted-foreground hover:text-red-400" onClick={() => setDeleteId(tx.id)}>
                        <Trash2 className="h-3 w-3" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Page <span className="tabular-nums">{page + 1}</span> of <span className="tabular-nums">{totalPages}</span>
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
              className="border-border/50 text-muted-foreground hover:text-foreground"
            >
              <ChevronLeft className="mr-1 h-4 w-4" /> Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage(page + 1)}
              className="border-border/50 text-muted-foreground hover:text-foreground"
            >
              Next <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {/* Delete confirmation */}
      <Dialog open={!!deleteId} onOpenChange={(o) => !o && setDeleteId(null)}>
        <DialogContent className="border-border/50 bg-card">
          <DialogHeader>
            <DialogTitle className="text-foreground">Delete Transaction</DialogTitle>
            <DialogDescription className="text-muted-foreground">
              Are you sure you want to delete this transaction? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteId(null)} className="border-border/50">Cancel</Button>
            <Button variant="destructive" onClick={handleDelete}>Delete</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
