import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  ArrowUpDown, Search, Trash2, Pencil, AlertCircle,
  ChevronLeft, ChevronRight, Upload, X, Filter,
} from 'lucide-react';
import { useGetTransactionsQuery, useDeleteTransactionMutation, useUpdateTransactionMutation } from '@/store/api/transaction-api';
import { useGetCategoriesQuery } from '@/store/api/category-api';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { CategoryBadge } from '@/components/ui/category-badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { formatCurrencyFull, formatDateShort, formatDateISO, getCategoryTone } from '@/lib/utils';
import { cn } from 'cn';
import { apiErrorMessage } from '@/lib/api-error';

const PAGE_SIZES = [10, 25, 50];

export default function TransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [category, setCategory] = useState(searchParams.get('category') || '');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState('date,desc');
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [editTx, setEditTx] = useState<any | null>(null);
  const [editForm, setEditForm] = useState<any>(null);

  const { data, isLoading } = useGetTransactionsQuery({ page, size, category: category || undefined, dateFrom: dateFrom || undefined, dateTo: dateTo || undefined, sort });
  const { data: categories = [] } = useGetCategoriesQuery();
  const [deleteTransaction] = useDeleteTransactionMutation();
  const [updateTransaction, { isLoading: isSaving }] = useUpdateTransactionMutation();
  const navigate = useNavigate();

  const transactions = data?.content || [];
  const totalPages = data?.totalPages || 0;
  const totalElements = data?.totalElements || 0;
  const hasFilters = !!(category || dateFrom || dateTo || search);

  const clearFilters = () => { setCategory(''); setDateFrom(''); setDateTo(''); setSearch(''); setPage(0); };

  const handleDelete = async () => {
    if (!deleteId) return;
    try { await deleteTransaction(deleteId).unwrap(); setDeleteId(null); toast.success('Transaction deleted'); }
    catch (err: any) { toast.error(err?.data?.detail || 'Failed to delete transaction'); }
  };

  const openEdit = (tx: any) => {
    setEditTx(tx);
    setEditForm({ date: formatDateISO(tx.date), rawDescription: tx.rawDescription, cleanMerchant: tx.cleanMerchant, amount: tx.amount, category: tx.category });
  };

  const canSave = !!editForm && !!editForm.date && !!editForm.cleanMerchant?.trim() && editForm.amount > 0 && !!editForm.category;

  const handleSave = async () => {
    if (!editTx || !editForm || !canSave) return;
    try {
      // Only the correctable fields; where the transaction came from (CSV, receipt) never changes.
      await updateTransaction({
        id: editTx.id,
        data: {
          date: editForm.date,
          rawDescription: editForm.rawDescription,
          cleanMerchant: editForm.cleanMerchant.trim(),
          amount: Math.abs(Number(editForm.amount) || 0),
          category: editForm.category,
        },
      }).unwrap();
      setEditTx(null); setEditForm(null);
      toast.success('Transaction updated');
    } catch (err) { toast.error(apiErrorMessage(err, 'Failed to update')); }
  };

  const from = page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="page-title">Transactions</h1>
          <p className="text-sm text-text-muted"><span className="tabular-nums">{totalElements}</span> transaction{totalElements !== 1 ? 's' : ''}</p>
        </div>
        <Button onClick={() => navigate('/upload')}><Upload className="size-4" />Import</Button>
      </div>

      {/* Filters bar */}
      <div className="flex flex-wrap items-end gap-3 rounded-[12px] border border-border-default bg-bg-surface p-4">
        <div className="flex flex-col gap-1.5">
          <label className="field-label">Category</label>
          <Select value={category} onValueChange={(v) => { setCategory(v); setPage(0); }}>
            <SelectTrigger className="w-40"><SelectValue placeholder="All categories" /></SelectTrigger>
            <SelectContent>
              {categories.map((c: string) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="field-label">From</label>
          <Input type="date" value={dateFrom} onChange={(e) => { setDateFrom(e.target.value); setPage(0); }} className="w-36" />
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="field-label">To</label>
          <Input type="date" value={dateTo} onChange={(e) => { setDateTo(e.target.value); setPage(0); }} className="w-36" />
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="field-label">Search</label>
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-text-muted" />
            <Input placeholder="Merchant or description…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} className="w-56 pl-8" />
          </div>
        </div>
        {hasFilters && (
          <Button variant="ghost" size="sm" onClick={clearFilters}><X className="size-3.5" />Clear filters</Button>
        )}
      </div>

      {/* Table */}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[104px]">
              <button onClick={() => setSort(sort === 'date,asc' ? 'date,desc' : 'date,asc')} className="flex items-center gap-1 hover:text-text-default">
                Date <ArrowUpDown className="size-3" />
              </button>
            </TableHead>
            <TableHead className="w-[170px]">
              <button onClick={() => setSort(sort === 'cleanMerchant,asc' ? 'cleanMerchant,desc' : 'cleanMerchant,asc')} className="flex items-center gap-1 hover:text-text-default">
                Merchant <ArrowUpDown className="size-3" />
              </button>
            </TableHead>
            <TableHead>Description</TableHead>
            <TableHead className="w-[120px] text-right">
              <button onClick={() => setSort(sort === 'amount,asc' ? 'amount,desc' : 'amount,asc')} className="flex items-center justify-end gap-1 hover:text-text-default w-full">
                Amount <ArrowUpDown className="size-3" />
              </button>
            </TableHead>
            <TableHead className="w-[168px]">Category</TableHead>
            <TableHead className="w-[84px]">Source</TableHead>
            <TableHead className="w-[24px]" />
            <TableHead className="w-[72px]" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading ? (
            Array.from({ length: 8 }).map((_, i) => (
              <TableRow key={i}>{Array.from({ length: 8 }).map((_, j) => <TableCell key={j}><Skeleton className="h-5 w-full" /></TableCell>)}</TableRow>
            ))
          ) : transactions.length === 0 ? (
            <TableRow>
              <TableCell colSpan={8} className="py-16 text-center text-text-muted">
                {hasFilters ? (
                  <div className="space-y-3">
                    <Filter className="mx-auto size-8 text-text-muted/50" />
                    <p>No transactions match your filters.</p>
                    <Button variant="secondary" size="sm" onClick={clearFilters}>Clear filters</Button>
                  </div>
                ) : (
                  <div className="space-y-3">
                    <Upload className="mx-auto size-8 text-text-muted/50" />
                    <p>No transactions yet.</p>
                    <Button variant="secondary" size="sm" onClick={() => navigate('/upload')}>Upload a CSV to get started</Button>
                  </div>
                )}
              </TableCell>
            </TableRow>
          ) : (
            transactions.map((tx: any) => (
              <TableRow key={tx.id} className={cn(tx.isDuplicate && 'bg-status-warning-soft')}>
                <TableCell className="text-mono text-sm text-text-muted tabular-nums">
                  {tx.isDuplicate && <AlertCircle className="size-4 text-status-warning inline mr-1.5" />}
                  {formatDateShort(tx.date)}
                </TableCell>
                <TableCell className="font-medium text-text-default">{tx.cleanMerchant}</TableCell>
                <TableCell className="max-w-48 truncate text-sm text-text-muted" title={tx.rawDescription}>{tx.rawDescription}</TableCell>
                <TableCell className="text-mono text-right text-sm font-semibold tabular-nums text-text-default">{formatCurrencyFull(tx.amount)}</TableCell>
                <TableCell><CategoryBadge category={tx.category} /></TableCell>
                <TableCell>
                  <span className="inline-flex items-center rounded-full bg-bg-muted px-2.5 py-0.5 text-xs font-medium text-text-muted uppercase tracking-wider">{tx.sourceType}</span>
                </TableCell>
                <TableCell>{tx.isDuplicate && <AlertCircle className="size-4 text-status-warning" />}</TableCell>
                <TableCell>
                  <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                    <Button variant="ghost" size="icon-sm" onClick={() => openEdit(tx)}><Pencil className="size-3" /></Button>
                    <Button variant="ghost" size="icon-sm" className="hover:text-status-danger" onClick={() => setDeleteId(tx.id)}><Trash2 className="size-3" /></Button>
                  </div>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>

      {/* Pagination */}
      {totalElements > 0 && (
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <p className="text-sm text-text-muted">
              Showing <span className="tabular-nums">{from}–{to}</span> of <span className="tabular-nums">{totalElements}</span>
            </p>
            <Select value={String(size)} onValueChange={(v) => { setSize(Number(v)); setPage(0); }}>
              <SelectTrigger size="sm" className="w-16"><SelectValue /></SelectTrigger>
              <SelectContent>
                {PAGE_SIZES.map(s => <SelectItem key={s} value={String(s)}>{s}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <div className="flex items-center gap-1">
            <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
              <ChevronLeft className="size-4" />
            </Button>
            {Array.from({ length: Math.min(totalPages, 7) }).map((_, i) => {
              const p = totalPages <= 7 ? i : i < 3 ? i : i === 3 ? -1 : i === 4 ? totalPages - 2 : i === 5 ? totalPages - 1 : -1;
              if (p === -1) return <span key={i} className="px-1 text-text-muted">…</span>;
              return (
                <Button key={p} variant={page === p ? 'default' : 'ghost'} size="sm" onClick={() => setPage(p)} className="min-w-[36px]">
                  {p + 1}
                </Button>
              );
            })}
            <Button variant="secondary" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </div>
      )}

      {/* Edit dialog */}
      <Dialog open={!!editTx} onOpenChange={(o) => { if (!o) { setEditTx(null); setEditForm(null); } }}>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>Edit Transaction</DialogTitle>
            <DialogDescription>Imported from {editTx?.sourceType || 'CSV'} on {editTx?.createdAt ? formatDateShort(editTx.createdAt) : '—'}</DialogDescription>
          </DialogHeader>
          {editForm && (
            <div className="flex flex-col gap-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-1.5">
                  <Label className="field-label">Date</Label>
                  <Input type="date" value={editForm.date} onChange={(e) => setEditForm({ ...editForm, date: e.target.value })} />
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label className="field-label">Amount</Label>
                  <Input type="number" step="0.01" value={editForm.amount} onChange={(e) => setEditForm({ ...editForm, amount: Math.abs(parseFloat(e.target.value) || 0) })} className="text-mono" />
                </div>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label className="field-label">Merchant</Label>
                <Input value={editForm.cleanMerchant} onChange={(e) => setEditForm({ ...editForm, cleanMerchant: e.target.value })} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label className="field-label">Description</Label>
                <Input value={editForm.rawDescription} onChange={(e) => setEditForm({ ...editForm, rawDescription: e.target.value })} />
                <p className="text-[11px] text-text-muted">Used for auto-categorization</p>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label className="field-label">Category</Label>
                <Select value={editForm.category} onValueChange={(v) => setEditForm({ ...editForm, category: v })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {categories.map((c: string) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="secondary" onClick={() => { setEditTx(null); setEditForm(null); }}>Cancel</Button>
            <Button onClick={handleSave} disabled={isSaving || !canSave}>{isSaving ? 'Saving…' : 'Save changes'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation */}
      <Dialog open={!!deleteId} onOpenChange={(o) => !o && setDeleteId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Transaction</DialogTitle>
            <DialogDescription>Are you sure you want to delete this transaction? This action cannot be undone.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secondary" onClick={() => setDeleteId(null)}>Cancel</Button>
            <Button variant="destructive" onClick={handleDelete}>Delete</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
