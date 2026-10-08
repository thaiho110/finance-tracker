import { useState, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { Upload, FileText, Camera, AlertCircle, Check, Trash2, X, Scan } from 'lucide-react';
import { useParseCsvMutation, useBatchSaveTransactionsMutation, useProcessOcrMutation } from '@/store/api/transaction-api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from 'cn';

type Tab = 'csv' | 'ocr';

interface ParsedRow {
  rowIndex: number;
  date: string;
  rawDescription: string;
  cleanMerchant: string;
  amount: number;
  category: string;
  isDuplicate: boolean;
}

export default function UploadPage() {
  const [tab, setTab] = useState<Tab>('csv');
  const [csvFile, setCsvFile] = useState<File | null>(null);
  const [ocrFile, setOcrFile] = useState<File | null>(null);
  const [parsedRows, setParsedRows] = useState<ParsedRow[]>([]);
  const [ocrResult, setOcrResult] = useState<any>(null);
  const [error, setError] = useState('');

  const [parseCsv, { isLoading: isParsing }] = useParseCsvMutation();
  const [processOcr, { isLoading: isProcessing }] = useProcessOcrMutation();
  const [batchSave, { isLoading: isSaving }] = useBatchSaveTransactionsMutation();

  const onCsvDrop = useCallback(async (accepted: File[]) => {
    const file = accepted[0];
    if (!file) return;
    setCsvFile(file);
    setError('');
    setParsedRows([]);

    const formData = new FormData();
    formData.append('file', file);

    try {
      const data = await parseCsv(formData).unwrap();
      setParsedRows(data);
    } catch (err: any) {
      setError(err?.data?.message || 'Failed to parse CSV');
    }
  }, [parseCsv]);

  const onOcrDrop = useCallback(async (accepted: File[]) => {
    const file = accepted[0];
    if (!file) return;
    setOcrFile(file);
    setError('');
    setOcrResult(null);

    const formData = new FormData();
    formData.append('image', file);

    try {
      const data = await processOcr(formData).unwrap();
      setOcrResult(data);
    } catch (err: any) {
      setError(err?.data?.message || 'Failed to process receipt');
    }
  }, [processOcr]);

  const csvDropzone = useDropzone({
    onDrop: onCsvDrop,
    accept: { 'text/csv': ['.csv'] },
    maxFiles: 1,
  });

  const ocrDropzone = useDropzone({
    onDrop: onOcrDrop,
    accept: { 'image/*': ['.jpg', '.jpeg', '.png'] },
    maxFiles: 1,
  });

  const handleSaveAll = async () => {
    try {
      const transactions = parsedRows.map((row) => ({
        date: row.date,
        rawDescription: row.rawDescription,
        cleanMerchant: row.cleanMerchant,
        amount: row.amount,
        category: row.category,
        sourceType: 'CSV',
      }));
      await batchSave(transactions).unwrap();
      setParsedRows([]);
      setCsvFile(null);
    } catch (err: any) {
      setError(err?.data?.message || 'Failed to save transactions');
    }
  };

  const handleSaveOcr = async () => {
    if (!ocrResult) return;
    try {
      const transaction = {
        date: ocrResult.date,
        rawDescription: ocrResult.merchant,
        cleanMerchant: ocrResult.merchant,
        amount: ocrResult.totalAmount,
        category: ocrResult.category,
        sourceType: 'OCR',
      };
      await batchSave([transaction]).unwrap();
      setOcrResult(null);
      setOcrFile(null);
    } catch (err: any) {
      setError(err?.data?.message || 'Failed to save transaction');
    }
  };

  const removeRow = (index: number) => {
    setParsedRows((prev) => prev.filter((_, i) => i !== index));
  };

  const updateRow = (index: number, field: keyof ParsedRow, value: string | number) => {
    setParsedRows((prev) =>
      prev.map((row, i) => (i === index ? { ...row, [field]: value } : row))
    );
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground">Import Transactions</h1>
        <p className="text-muted-foreground">Upload a CSV from your bank or scan a receipt</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 rounded-xl border border-border/50 bg-card/50 p-1">
        <button
          onClick={() => setTab('csv')}
          className={cn(
            'flex flex-1 items-center justify-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-200',
            tab === 'csv'
              ? 'bg-background text-foreground shadow-sm'
              : 'text-muted-foreground hover:text-foreground'
          )}
        >
          <FileText className="h-4 w-4" />
          CSV Upload
        </button>
        <button
          onClick={() => setTab('ocr')}
          className={cn(
            'flex flex-1 items-center justify-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-200',
            tab === 'ocr'
              ? 'bg-background text-foreground shadow-sm'
              : 'text-muted-foreground hover:text-foreground'
          )}
        >
          <Camera className="h-4 w-4" />
          Receipt Scan
        </button>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-lg border border-destructive/20 bg-destructive/10 p-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
          <Button variant="ghost" size="icon" className="ml-auto h-5 w-5 text-destructive/70 hover:text-destructive" onClick={() => setError('')}>
            <X className="h-3 w-3" />
          </Button>
        </div>
      )}

      {/* CSV Tab */}
      {tab === 'csv' && (
        <>
          {parsedRows.length === 0 ? (
            <div
              {...csvDropzone.getRootProps()}
              className={cn(
                'flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed p-16 transition-all duration-200',
                csvDropzone.isDragActive
                  ? 'border-emerald-500 bg-emerald-500/5'
                  : 'border-border/50 hover:border-emerald-500/50 hover:bg-muted/20'
              )}
            >
              <input {...csvDropzone.getInputProps()} />
              <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-500/20 to-emerald-600/10">
                <Upload className={cn('h-6 w-6 transition-colors', csvDropzone.isDragActive ? 'text-emerald-400' : 'text-muted-foreground')} />
              </div>
              <p className="mb-1 text-base font-medium text-foreground">
                {csvDropzone.isDragActive ? 'Drop your CSV here' : 'Drop CSV here or click to browse'}
              </p>
              <p className="text-sm text-muted-foreground">Supported: CSV files from your bank. Maximum 10MB.</p>
              {csvFile && (
                <div className="mt-4 flex items-center gap-2 rounded-lg bg-muted/50 px-3 py-2 text-sm text-muted-foreground">
                  <FileText className="h-4 w-4" />
                  {csvFile.name}
                </div>
              )}
            </div>
          ) : (
            <Card className="border-border/50">
              <CardHeader>
                <CardTitle className="text-foreground">Preview</CardTitle>
                <CardDescription className="text-muted-foreground">
                  <span className="tabular-nums">{parsedRows.length}</span> transaction{parsedRows.length !== 1 ? 's' : ''} found. Review and edit before saving.
                </CardDescription>
              </CardHeader>
              <CardContent className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow className="border-border/50">
                      <TableHead className="w-8" />
                      <TableHead className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Date</TableHead>
                      <TableHead className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Description</TableHead>
                      <TableHead className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Merchant</TableHead>
                      <TableHead className="text-right text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Amount</TableHead>
                      <TableHead className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Category</TableHead>
                      <TableHead className="w-8" />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {parsedRows.map((row, i) => (
                      <TableRow key={i} className={cn('border-border/50', row.isDuplicate && 'bg-yellow-500/5')}>
                        <TableCell>
                          {row.isDuplicate && <AlertCircle className="h-4 w-4 text-yellow-500" title="Possible duplicate" />}
                        </TableCell>
                        <TableCell>
                          <Input
                            type="date"
                            value={row.date}
                            onChange={(e) => updateRow(i, 'date', e.target.value)}
                            className="h-8 w-36 border-border/50 bg-background/50 text-foreground"
                          />
                        </TableCell>
                        <TableCell>
                          <Input
                            value={row.rawDescription}
                            onChange={(e) => updateRow(i, 'rawDescription', e.target.value)}
                            className="h-8 w-48 border-border/50 bg-background/50 text-foreground"
                          />
                        </TableCell>
                        <TableCell>
                          <Input
                            value={row.cleanMerchant}
                            onChange={(e) => updateRow(i, 'cleanMerchant', e.target.value)}
                            className="h-8 w-40 border-border/50 bg-background/50 text-foreground"
                          />
                        </TableCell>
                        <TableCell>
                          <Input
                            type="number"
                            step="0.01"
                            value={row.amount}
                            onChange={(e) => updateRow(i, 'amount', parseFloat(e.target.value) || 0)}
                            className="h-8 w-28 border-border/50 bg-background/50 text-right tabular-nums text-foreground"
                          />
                        </TableCell>
                        <TableCell>
                          <Select
                            value={row.category}
                            onValueChange={(v) => updateRow(i, 'category', v)}
                          >
                            <SelectTrigger className="h-8 w-36 border-border/50 bg-background/50 text-foreground">
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
                        </TableCell>
                        <TableCell>
                          <Button variant="ghost" size="icon" className="h-8 w-8 text-muted-foreground hover:text-red-400" onClick={() => removeRow(i)}>
                            <Trash2 className="h-3 w-3" />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
              <div className="flex items-center justify-between border-t border-border/50 p-4">
                <p className="text-sm text-muted-foreground">
                  <span className="tabular-nums">{parsedRows.length}</span> transaction{parsedRows.length !== 1 ? 's' : ''} to save
                </p>
                <div className="flex gap-2">
                  <Button variant="outline" onClick={() => { setParsedRows([]); setCsvFile(null); }} className="border-border/50 text-muted-foreground hover:text-foreground">
                    Discard
                  </Button>
                  <Button
                    onClick={handleSaveAll}
                    disabled={isSaving || parsedRows.length === 0}
                    className="bg-gradient-to-r from-emerald-500 to-emerald-600 text-white shadow-lg shadow-emerald-500/20 hover:from-emerald-400 hover:to-emerald-500"
                  >
                    {isSaving ? (
                      <>Saving...</>
                    ) : (
                      <>
                        <Check className="h-4 w-4" />
                        Save {parsedRows.length} transaction{parsedRows.length !== 1 ? 's' : ''}
                      </>
                    )}
                  </Button>
                </div>
              </div>
            </Card>
          )}

          {/* Quick Scan button — shown when no data is being previewed */}
          {parsedRows.length === 0 && !csvFile && (
            <div className="text-center">
              <p className="mb-3 text-sm text-muted-foreground">
                Already uploaded before? Import your latest data quickly.
              </p>
              <Button variant="outline" className="border-border/50 text-muted-foreground hover:text-foreground">
                <Scan className="mr-2 h-4 w-4" />
                Quick Scan
              </Button>
            </div>
          )}
        </>
      )}

      {/* OCR Tab */}
      {tab === 'ocr' && (
        <>
          {!ocrResult ? (
            <div
              {...ocrDropzone.getRootProps()}
              className={cn(
                'flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed p-16 transition-all duration-200',
                ocrDropzone.isDragActive
                  ? 'border-emerald-500 bg-emerald-500/5'
                  : 'border-border/50 hover:border-emerald-500/50 hover:bg-muted/20'
              )}
            >
              <input {...ocrDropzone.getInputProps()} />
              <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-500/20 to-emerald-600/10">
                <Camera className={cn('h-6 w-6 transition-colors', ocrDropzone.isDragActive ? 'text-emerald-400' : 'text-muted-foreground')} />
              </div>
              <p className="mb-1 text-base font-medium text-foreground">
                {ocrDropzone.isDragActive ? 'Drop your receipt image here' : 'Drop receipt image or click to browse'}
              </p>
              <p className="text-sm text-muted-foreground">Supported: JPG, PNG</p>
              {ocrFile && (
                <div className="mt-4 flex items-center gap-2 rounded-lg bg-muted/50 px-3 py-2 text-sm text-muted-foreground">
                  <FileText className="h-4 w-4" />
                  {ocrFile.name}
                </div>
              )}
            </div>
          ) : (
            <Card className="border-border/50">
              <CardHeader>
                <CardTitle className="text-foreground">Receipt Details</CardTitle>
                <CardDescription className="text-muted-foreground">Review the extracted information before saving</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="rounded-lg bg-muted/30 p-3">
                    <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Merchant</Label>
                    <p className="mt-1 font-medium text-foreground">{ocrResult.merchant}</p>
                  </div>
                  <div className="rounded-lg bg-muted/30 p-3">
                    <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Date</Label>
                    <p className="mt-1 font-medium text-foreground">{ocrResult.date}</p>
                  </div>
                  <div className="rounded-lg bg-muted/30 p-3">
                    <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Total Amount</Label>
                    <p className="mt-1 text-xl font-bold tabular-nums text-emerald-400">
                      ${Number(ocrResult.totalAmount).toFixed(2)}
                    </p>
                  </div>
                  <div className="rounded-lg bg-muted/30 p-3">
                    <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Category</Label>
                    <div className="mt-1">
                      <Badge variant="secondary" className="border-0 bg-muted text-xs font-normal text-muted-foreground">
                        {ocrResult.category}
                      </Badge>
                    </div>
                  </div>
                </div>
                {ocrResult.isDuplicate && (
                  <div className="flex items-center gap-2 rounded-lg border border-yellow-500/20 bg-yellow-500/10 p-3 text-sm text-yellow-400">
                    <AlertCircle className="h-4 w-4 shrink-0" />
                    This appears to be a duplicate transaction
                  </div>
                )}
                {ocrResult.lineItems && ocrResult.lineItems.length > 0 && (
                  <div>
                    <Label className="mb-2 block text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Line Items</Label>
                    <div className="rounded-lg border border-border/50 overflow-hidden">
                      <Table>
                        <TableHeader>
                          <TableRow className="border-border/50 bg-muted/30">
                            <TableHead className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Item</TableHead>
                            <TableHead className="text-right text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Price</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {ocrResult.lineItems.map((item: any, i: number) => (
                            <TableRow key={i} className="border-border/50">
                              <TableCell className="text-foreground">{item.item}</TableCell>
                              <TableCell className="text-right tabular-nums text-foreground">${Number(item.price).toFixed(2)}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </div>
                  </div>
                )}
              </CardContent>
              <div className="flex items-center justify-end gap-2 border-t border-border/50 p-4">
                <Button variant="outline" onClick={() => { setOcrResult(null); setOcrFile(null); }} className="border-border/50 text-muted-foreground hover:text-foreground">
                  Discard
                </Button>
                <Button
                  onClick={handleSaveOcr}
                  disabled={isSaving}
                  className="bg-gradient-to-r from-emerald-500 to-emerald-600 text-white shadow-lg shadow-emerald-500/20 hover:from-emerald-400 hover:to-emerald-500"
                >
                  <Check className="h-4 w-4" />
                  Save as Transaction
                </Button>
              </div>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
