import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Save, Pencil } from 'lucide-react';
import { useGetTransactionQuery, useUpdateTransactionMutation } from '@/store/api/transaction-api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

function formatCurrency(n: number) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 }).format(Math.abs(n));
}

export default function TransactionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: tx, isLoading } = useGetTransactionQuery(id!);
  const [updateTransaction] = useUpdateTransactionMutation();

  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<any>(null);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-32 bg-muted/50" />
        <Card className="border-border/50"><CardContent className="p-6"><Skeleton className="h-48 w-full bg-muted/30" /></CardContent></Card>
      </div>
    );
  }

  if (!tx) {
    return (
      <div className="py-12 text-center text-muted-foreground">
        Transaction not found
      </div>
    );
  }

  const handleSave = async () => {
    if (!form) return;
    try {
      await updateTransaction({ id: id!, data: form }).unwrap();
      setEditing(false);
    } catch {}
  };

  const startEditing = () => {
    setForm({
      date: tx.date,
      rawDescription: tx.rawDescription,
      cleanMerchant: tx.cleanMerchant,
      amount: tx.amount,
      category: tx.category,
      sourceType: tx.sourceType,
    });
    setEditing(true);
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button
          variant="ghost"
          size="icon"
          onClick={() => navigate(-1)}
          className="text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">{tx.cleanMerchant}</h1>
          <p className="text-muted-foreground">Transaction details</p>
        </div>
      </div>

      <Card className="border-border/50">
        <CardHeader>
          <CardTitle className="text-foreground">Details</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {editing ? (
            <>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label className="text-xs font-medium text-muted-foreground">Date</Label>
                  <Input
                    type="date"
                    value={form.date}
                    onChange={(e) => setForm({ ...form, date: e.target.value })}
                    className="border-border/50 bg-background/50 text-foreground"
                  />
                </div>
                <div className="space-y-2">
                  <Label className="text-xs font-medium text-muted-foreground">Amount</Label>
                  <Input
                    type="number"
                    step="0.01"
                    value={form.amount}
                    onChange={(e) => setForm({ ...form, amount: parseFloat(e.target.value) || 0 })}
                    className="border-border/50 bg-background/50 text-foreground"
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label className="text-xs font-medium text-muted-foreground">Merchant</Label>
                <Input
                  value={form.cleanMerchant}
                  onChange={(e) => setForm({ ...form, cleanMerchant: e.target.value })}
                  className="border-border/50 bg-background/50 text-foreground"
                />
              </div>
              <div className="space-y-2">
                <Label className="text-xs font-medium text-muted-foreground">Description</Label>
                <Input
                  value={form.rawDescription}
                  onChange={(e) => setForm({ ...form, rawDescription: e.target.value })}
                  className="border-border/50 bg-background/50 text-foreground"
                />
              </div>
              <div className="space-y-2">
                <Label className="text-xs font-medium text-muted-foreground">Category</Label>
                <Select value={form.category} onValueChange={(v) => setForm({ ...form, category: v })}>
                  <SelectTrigger className="border-border/50 bg-background/50 text-foreground">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Food & Dining">Food & Dining</SelectItem>
                    <SelectItem value="Transportation">Transportation</SelectItem>
                    <SelectItem value="Shopping">Shopping</SelectItem>
                    <SelectItem value="Bills & Utilities">Bills & Utilities</SelectItem>
                    <SelectItem value="Entertainment">Entertainment</SelectItem>
                    <SelectItem value="Healthcare">Healthcare</SelectItem>
                    <SelectItem value="Income">Income</SelectItem>
                    <SelectItem value="Other">Other</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <Button variant="outline" onClick={() => setEditing(false)} className="border-border/50 text-muted-foreground hover:text-foreground">
                  Cancel
                </Button>
                <Button
                  onClick={handleSave}
                  className="bg-gradient-to-r from-emerald-500 to-emerald-600 text-white shadow-lg shadow-emerald-500/20 hover:from-emerald-400 hover:to-emerald-500"
                >
                  <Save className="mr-2 h-4 w-4" /> Save
                </Button>
              </div>
            </>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-4">
                <div className="rounded-lg bg-muted/30 p-3">
                  <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Date</Label>
                  <p className="mt-1 font-medium text-foreground tabular-nums">{tx.date}</p>
                </div>
                <div className="rounded-lg bg-muted/30 p-3">
                  <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Amount</Label>
                  <p className={`mt-1 text-xl font-bold tabular-nums ${tx.amount < 0 ? 'text-red-400' : 'text-emerald-400'}`}>
                    {tx.amount < 0 ? '-' : '+'}{formatCurrency(tx.amount)}
                  </p>
                </div>
              </div>
              <div className="rounded-lg bg-muted/30 p-3">
                <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Merchant</Label>
                <p className="mt-1 font-medium text-foreground">{tx.cleanMerchant}</p>
              </div>
              <div className="rounded-lg bg-muted/30 p-3">
                <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Description</Label>
                <p className="mt-1 text-sm text-muted-foreground">{tx.rawDescription}</p>
              </div>
              <div className="flex flex-wrap gap-4">
                <div className="rounded-lg bg-muted/30 p-3">
                  <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Category</Label>
                  <div className="mt-1">
                    <Badge variant="secondary" className="border-0 bg-muted text-xs font-normal text-muted-foreground">{tx.category}</Badge>
                  </div>
                </div>
                <div className="rounded-lg bg-muted/30 p-3">
                  <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Source</Label>
                  <div className="mt-1">
                    <Badge variant="outline" className="border-border/50 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">{tx.sourceType}</Badge>
                  </div>
                </div>
                {tx.isDuplicate && (
                  <div className="rounded-lg bg-muted/30 p-3">
                    <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Status</Label>
                    <div className="mt-1">
                      <Badge variant="outline" className="border-yellow-500/50 text-yellow-400">Duplicate</Badge>
                    </div>
                  </div>
                )}
              </div>
              {tx.receiptItems && tx.receiptItems.length > 0 && (
                <div>
                  <Label className="mb-2 block text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Receipt Items</Label>
                  <div className="rounded-lg border border-border/50 overflow-hidden">
                    <Table>
                      <TableHeader>
                        <TableRow className="border-border/50 bg-muted/30">
                          <TableHead className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Item</TableHead>
                          <TableHead className="text-right text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Price</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {tx.receiptItems.map((item: any, i: number) => (
                          <TableRow key={i} className="border-border/50">
                            <TableCell className="text-foreground">{item.itemDescription}</TableCell>
                            <TableCell className="text-right tabular-nums text-foreground">${Number(item.price).toFixed(2)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                </div>
              )}
              <div className="flex justify-end pt-2">
                <Button
                  onClick={startEditing}
                  className="bg-gradient-to-r from-emerald-500 to-emerald-600 text-white shadow-lg shadow-emerald-500/20 hover:from-emerald-400 hover:to-emerald-500"
                >
                  <Pencil className="mr-2 h-4 w-4" /> Edit
                </Button>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
