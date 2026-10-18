import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Pencil, Loader2, AlertTriangle, Check } from 'lucide-react';
import { useGetTransactionQuery, useUpdateTransactionMutation } from '@/store/api/transaction-api';
import { useGetCategoriesQuery } from '@/store/api/category-api';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { CategoryBadge } from '@/components/ui/category-badge';
import { Card, CardContent, CardHeader, CardTitle, CardDescription, CardFooter } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatCurrencyFull, formatDateISO } from '@/lib/utils';
import { apiErrorMessage } from '@/lib/api-error';

const SOURCE_LABELS: Record<string, string> = { CSV: 'CSV', OCR: 'Receipt', MANUAL: 'Manual' };

function formatLongDate(dateStr: string): string {
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="field-label">{label}</span>
      <div className="text-sm text-text-default">{children}</div>
    </div>
  );
}

export default function TransactionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: tx, isLoading } = useGetTransactionQuery(id!);
  const { data: categories = [] } = useGetCategoriesQuery();
  const [updateTransaction, { isLoading: isSaving }] = useUpdateTransactionMutation();

  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<any>(null);

  if (isLoading) {
    return (
      <div className="mx-auto flex max-w-2xl flex-col gap-6">
        <Skeleton className="h-8 w-48" />
        <Card><Skeleton className="h-48 w-full" /></Card>
      </div>
    );
  }

  if (!tx) {
    return (
      <div className="mx-auto flex max-w-2xl flex-col items-center gap-3 py-16 text-center">
        <p className="text-sm text-text-muted">This transaction doesn't exist or was deleted.</p>
        <Button variant="secondary" onClick={() => navigate('/transactions')}>Back to transactions</Button>
      </div>
    );
  }

  const categoryOptions: string[] = form?.category && !categories.includes(form.category)
    ? [form.category, ...categories]
    : categories;

  const startEditing = () => {
    setForm({
      date: formatDateISO(tx.date),
      rawDescription: tx.rawDescription,
      cleanMerchant: tx.cleanMerchant,
      amount: Math.abs(Number(tx.amount) || 0),
      category: tx.category,
    });
    setEditing(true);
  };

  const canSave = !!form && !!form.date && !!form.cleanMerchant?.trim() && form.amount > 0;

  const handleSave = async () => {
    if (!canSave) return;
    try {
      await updateTransaction({ id: id!, data: { ...form, cleanMerchant: form.cleanMerchant.trim() } }).unwrap();
      setEditing(false);
      toast.success('Transaction updated');
    } catch (err: any) {
      toast.error(apiErrorMessage(err, 'Failed to update transaction'));
    }
  };

  const receiptItems: any[] = tx.receiptItems || [];
  const itemsTotal = receiptItems.reduce((sum: number, item: any) => sum + (Number(item.price) || 0), 0);

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" aria-label="Back" onClick={() => navigate(-1)}>
          <ArrowLeft className="size-4" />
        </Button>
        <div className="min-w-0 flex-1">
          <h1 className="page-title truncate">{tx.cleanMerchant}</h1>
          <p className="text-sm text-text-muted">{formatLongDate(tx.date)}</p>
        </div>
        <p className="text-mono text-[28px] font-semibold leading-9 tracking-tight text-text-default">
          {formatCurrencyFull(tx.amount)}
        </p>
      </div>

      {tx.isDuplicate && (
        <div className="flex items-center gap-2.5 rounded-[8px] bg-status-warning-soft px-3 py-2.5">
          <AlertTriangle className="size-4 shrink-0 text-status-warning" />
          <p className="text-sm text-text-default">
            <span className="font-medium text-status-warning">Possible duplicate.</span>{' '}
            Another transaction with the same amount was recorded within 2 days of this one.
          </p>
        </div>
      )}

      <Card>
        <div className="flex items-start justify-between gap-3">
          <CardHeader>
            <CardTitle>{editing ? 'Edit transaction' : 'Details'}</CardTitle>
            <CardDescription>
              Imported from {SOURCE_LABELS[tx.sourceType] || tx.sourceType}
              {tx.createdAt ? <> on {formatLongDate(tx.createdAt)}</> : null}
            </CardDescription>
          </CardHeader>
          {!editing && (
            <Button variant="secondary" onClick={startEditing}>
              <Pencil className="size-4" />Edit
            </Button>
          )}
        </div>

        <CardContent>
          {editing ? (
            <div className="flex flex-col gap-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="tx-date" className="field-label">Date</Label>
                  <Input
                    id="tx-date"
                    type="date"
                    value={form.date}
                    aria-invalid={!form.date}
                    onChange={(e) => setForm({ ...form, date: e.target.value })}
                    className="text-mono"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="tx-amount" className="field-label">Amount (₫)</Label>
                  <Input
                    id="tx-amount"
                    type="number"
                    inputMode="numeric"
                    min={0}
                    step={1}
                    value={form.amount}
                    aria-invalid={!(form.amount > 0)}
                    onChange={(e) => setForm({ ...form, amount: Math.abs(Number(e.target.value) || 0) })}
                    className="text-mono"
                  />
                </div>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="tx-merchant" className="field-label">Merchant</Label>
                <Input
                  id="tx-merchant"
                  value={form.cleanMerchant}
                  aria-invalid={!form.cleanMerchant?.trim()}
                  onChange={(e) => setForm({ ...form, cleanMerchant: e.target.value })}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="tx-description" className="field-label">Description</Label>
                <Input
                  id="tx-description"
                  value={form.rawDescription}
                  onChange={(e) => setForm({ ...form, rawDescription: e.target.value })}
                />
                <p className="text-xs text-text-muted">Original bank description — used for auto-categorization</p>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label className="field-label">Category</Label>
                <Select value={form.category} onValueChange={(v) => setForm({ ...form, category: v })}>
                  <SelectTrigger className="h-9 w-full">
                    <SelectValue>{form.category ? <CategoryBadge category={form.category} /> : null}</SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {categoryOptions.map((c) => (
                      <SelectItem key={c} value={c}><CategoryBadge category={c} /></SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-x-6 gap-y-5">
              <Field label="Merchant"><span className="font-medium">{tx.cleanMerchant}</span></Field>
              <Field label="Date"><span className="text-mono">{formatDateISO(tx.date)}</span></Field>
              <div className="col-span-2">
                <Field label="Description">
                  <span className="break-words text-text-muted">{tx.rawDescription}</span>
                </Field>
              </div>
              <Field label="Category"><CategoryBadge category={tx.category} /></Field>
              <Field label="Source">
                <span className="inline-flex items-center rounded-full bg-bg-muted px-2.5 py-0.5 text-xs font-medium uppercase tracking-wider text-text-muted">
                  {SOURCE_LABELS[tx.sourceType] || tx.sourceType}
                </span>
              </Field>
            </div>
          )}
        </CardContent>

        {editing && (
          <CardFooter className="justify-end gap-2">
            <Button variant="secondary" onClick={() => setEditing(false)} disabled={isSaving}>Cancel</Button>
            <Button onClick={handleSave} disabled={!canSave || isSaving}>
              {isSaving ? <><Loader2 className="size-4 animate-spin" />Saving…</> : 'Save changes'}
            </Button>
          </CardFooter>
        )}
      </Card>

      {receiptItems.length > 0 && (
        <div className="flex flex-col gap-2">
          <span className="field-label">Receipt items</span>
          <Table>
            <TableHeader>
              <TableRow className="bg-bg-muted hover:bg-bg-muted">
                <TableHead className="h-[34px]">Item ({receiptItems.length})</TableHead>
                <TableHead className="h-[34px] text-right">Price (₫)</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {receiptItems.map((item: any, i: number) => (
                <TableRow key={item.id ?? i}>
                  <TableCell className="text-text-default">{item.itemDescription}</TableCell>
                  <TableCell className="text-mono text-right text-text-default">
                    {new Intl.NumberFormat('en-US').format(Number(item.price) || 0)}
                  </TableCell>
                </TableRow>
              ))}
              <TableRow className="hover:bg-transparent">
                <TableCell className="text-xs font-medium text-text-muted">
                  {Math.round(itemsTotal) === Math.round(Math.abs(Number(tx.amount) || 0))
                    ? <span className="flex items-center gap-1.5 text-brand-primary"><Check className="size-3.5" />Items add up to total</span>
                    : 'Items total'}
                </TableCell>
                <TableCell className="text-mono text-right font-semibold text-text-default">
                  {new Intl.NumberFormat('en-US').format(itemsTotal)}
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
