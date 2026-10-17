import { useState, useCallback, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDropzone, type FileRejection } from 'react-dropzone';
import {
  Upload, FileSpreadsheet, Camera, AlertTriangle, Check, Trash2, Loader2,
  Search, RotateCcw, ShieldCheck, FileText, ArrowRight,
} from 'lucide-react';
import { toast } from 'sonner';
import { useParseCsvMutation, useBatchSaveTransactionsMutation, useProcessOcrMutation } from '@/store/api/transaction-api';
import { useGetCategoriesQuery } from '@/store/api/category-api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { CategoryBadge } from '@/components/ui/category-badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatCurrencyFull, formatDateISO } from '@/lib/utils';
import { cn } from 'cn';

type Tab = 'csv' | 'ocr';

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB — matches the backend multipart limit

interface ParsedRow {
  rowIndex: number;
  date: string;
  rawDescription: string;
  cleanMerchant: string;
  amount: number;
  category: string;
  isDuplicate: boolean;
}

interface LineItem {
  item: string;
  price: number;
}

interface OcrResult {
  merchant: string | null;
  date: string | null;
  totalAmount: number | null;
  category: string | null;
  isDuplicate: boolean;
  lineItems: LineItem[];
}

interface ReceiptForm {
  merchant: string;
  date: string;
  amount: number;
  category: string;
}

/* ───────────────────────── helpers ───────────────────────── */

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function apiErrorMessage(err: any, fallback: string): string {
  return err?.data?.detail || err?.data?.message || fallback;
}

function rejectionMessage(rejections: FileRejection[], accepted: string): string {
  const code = rejections[0]?.errors[0]?.code;
  if (code === 'file-too-large') return 'This file is larger than 10MB.';
  if (code === 'file-invalid-type') return `Unsupported file type. Use ${accepted}.`;
  return 'This file could not be used.';
}

const todayISO = () => new Date().toISOString().slice(0, 10);

/* ───────────────────────── shared pieces ───────────────────────── */

function Tabs({ tab, onChange }: { tab: Tab; onChange: (t: Tab) => void }) {
  const items: { id: Tab; label: string; icon: typeof FileSpreadsheet }[] = [
    { id: 'csv', label: 'CSV Upload', icon: FileSpreadsheet },
    { id: 'ocr', label: 'Receipt Scan', icon: Camera },
  ];
  return (
    <div role="tablist" className="inline-flex gap-1 rounded-[10px] bg-bg-muted p-1">
      {items.map(({ id, label, icon: Icon }) => (
        <button
          key={id}
          role="tab"
          aria-selected={tab === id}
          onClick={() => onChange(id)}
          className={cn(
            'flex items-center gap-2 rounded-[7px] px-3.5 py-1.5 text-sm font-medium transition-colors',
            tab === id
              ? 'bg-bg-surface text-text-default shadow-sm'
              : 'text-text-muted hover:text-text-default'
          )}
        >
          <Icon className="size-4" />
          {label}
        </button>
      ))}
    </div>
  );
}

/** Large dashed drop area used by both tabs in their empty state. */
function DropArea({
  rootProps, inputProps, isDragActive, icon: Icon, title, subtitle, browseLabel, helper, onBrowse,
}: {
  rootProps: ReturnType<ReturnType<typeof useDropzone>['getRootProps']>;
  inputProps: ReturnType<ReturnType<typeof useDropzone>['getInputProps']>;
  isDragActive: boolean;
  icon: typeof Upload;
  title: string;
  subtitle: string;
  browseLabel: string;
  helper: string;
  onBrowse: () => void;
}) {
  return (
    <div
      {...rootProps}
      className={cn(
        'flex min-h-[420px] cursor-pointer flex-col items-center justify-center gap-3 rounded-[16px] border-[1.5px] border-dashed bg-bg-surface px-6 text-center transition-colors',
        isDragActive
          ? 'border-brand-primary bg-brand-primary-soft/40'
          : 'border-border-strong hover:border-brand-primary/60'
      )}
    >
      <input {...inputProps} />
      <div className="mb-1 flex size-14 items-center justify-center rounded-full bg-brand-primary-soft">
        <Icon className="size-6 text-brand-primary" />
      </div>
      <p className="text-xl font-semibold text-text-default">{isDragActive ? 'Release to upload' : title}</p>
      <p className="text-sm text-text-muted">{subtitle}</p>
      <Button
        variant="secondary"
        className="mt-2"
        onClick={(e) => { e.stopPropagation(); onBrowse(); }}
      >
        <FileSpreadsheet className="size-4" />
        {browseLabel}
      </Button>
      <p className="mt-2 text-xs font-medium text-text-muted">{helper}</p>
    </div>
  );
}

/** Inline error panel with a retry action (parse / OCR failures). */
function ErrorPanel({ title, detail, actionLabel, onAction }: { title: string; detail?: string; actionLabel: string; onAction: () => void }) {
  return (
    <div className="flex items-start gap-3 rounded-[12px] bg-status-danger-soft p-4">
      <AlertTriangle className="mt-0.5 size-5 shrink-0 text-status-danger" />
      <div className="flex-1">
        <p className="text-sm font-medium text-status-danger">{title}</p>
        {detail && <p className="mt-0.5 text-sm text-text-default">{detail}</p>}
      </div>
      <Button variant="secondary" size="sm" onClick={onAction}>{actionLabel}</Button>
    </div>
  );
}

function Chip({ tone, icon: Icon, children }: { tone: 'success' | 'warning'; icon: typeof Check; children: React.ReactNode }) {
  return (
    <span className={cn(
      'inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium',
      tone === 'success' ? 'bg-brand-primary-soft text-brand-primary' : 'bg-status-warning-soft text-status-warning'
    )}>
      <Icon className="size-3.5" />
      {children}
    </span>
  );
}

/** Sticky action bar pinned to the bottom of the scrolling main area. */
function SaveBar({ title, subtitle, subtitleTone = 'muted', children }: {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  subtitleTone?: 'muted' | 'warning';
  children: React.ReactNode;
}) {
  return (
    <div className="sticky bottom-0 z-10 -mx-8 -mb-6 mt-6 flex items-center gap-3 border-t border-border-default bg-bg-surface px-8 py-3.5 shadow-[0_-4px_12px_rgba(0,0,0,0.05)]">
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-text-default">{title}</p>
        {subtitle && (
          <p className={cn('truncate text-xs', subtitleTone === 'warning' ? 'text-status-warning' : 'text-text-muted')}>{subtitle}</p>
        )}
      </div>
      {children}
    </div>
  );
}

function CategorySelect({ value, categories, onChange, className }: {
  value: string;
  categories: string[];
  onChange: (v: string) => void;
  className?: string;
}) {
  const options = useMemo(
    () => (value && !categories.includes(value) ? [value, ...categories] : categories),
    [value, categories]
  );
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className={cn('h-9 w-full bg-bg-surface', className)}>
        <SelectValue placeholder="Choose category">
          {value ? <CategoryBadge category={value} /> : null}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        {options.map((c) => (
          <SelectItem key={c} value={c}><CategoryBadge category={c} /></SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

/* ───────────────────────── page ───────────────────────── */

export default function UploadPage() {
  const [tab, setTab] = useState<Tab>('csv');
  const { data: apiCategories = [] } = useGetCategoriesQuery();

  return (
    <div className="flex min-h-full flex-col gap-5">
      <Tabs tab={tab} onChange={setTab} />
      {tab === 'csv'
        ? <CsvImport categories={apiCategories} />
        : <ReceiptScan categories={apiCategories} />}
    </div>
  );
}

/* ───────────────────────── CSV tab ───────────────────────── */

function CsvImport({ categories }: { categories: string[] }) {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [lastSavedFile, setLastSavedFile] = useState<File | null>(null);
  const [rows, setRows] = useState<ParsedRow[]>([]);
  const [parseTimeMs, setParseTimeMs] = useState<number | null>(null);
  const [error, setError] = useState<{ title: string; detail?: string } | null>(null);
  const [filter, setFilter] = useState('');
  const [savedCount, setSavedCount] = useState<number | null>(null);

  const [parseCsv, { isLoading: isParsing }] = useParseCsvMutation();
  const [batchSave, { isLoading: isSaving }] = useBatchSaveTransactionsMutation();

  const parseFile = useCallback(async (f: File) => {
    setFile(f);
    setError(null);
    setRows([]);
    setFilter('');
    setSavedCount(null);
    const formData = new FormData();
    formData.append('file', f);
    const started = performance.now();
    try {
      const data = await parseCsv(formData).unwrap();
      setParseTimeMs(performance.now() - started);
      const parsed: ParsedRow[] = (data || []).map((r: any) => ({
        ...r,
        date: r.date ? formatDateISO(r.date) : '',
        amount: Math.abs(Number(r.amount) || 0),
      }));
      if (parsed.length === 0) {
        setError({ title: 'Could not parse this file.', detail: 'No transactions were found in it.' });
        return;
      }
      setRows(parsed);
    } catch (err: any) {
      setError({ title: 'Could not parse this file.', detail: apiErrorMessage(err, 'Check that it is a CSV export from your bank.') });
    }
  }, [parseCsv]);

  const onDrop = useCallback((accepted: File[], rejected: FileRejection[]) => {
    if (rejected.length) {
      setError({ title: 'Could not use this file.', detail: rejectionMessage(rejected, 'a .csv file') });
      return;
    }
    if (accepted[0]) parseFile(accepted[0]);
  }, [parseFile]);

  const dropzone = useDropzone({
    onDrop,
    accept: { 'text/csv': ['.csv'] },
    maxFiles: 1,
    maxSize: MAX_FILE_SIZE,
    noClick: rows.length > 0,
    noKeyboard: rows.length > 0,
  });

  const reset = () => {
    setFile(null);
    setRows([]);
    setError(null);
    setFilter('');
    setParseTimeMs(null);
  };

  const updateRow = (rowIndex: number, patch: Partial<ParsedRow>) =>
    setRows((prev) => prev.map((r) => (r.rowIndex === rowIndex ? { ...r, ...patch } : r)));

  const removeRow = (rowIndex: number) =>
    setRows((prev) => prev.filter((r) => r.rowIndex !== rowIndex));

  const visibleRows = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) =>
      r.rawDescription.toLowerCase().includes(q) || r.cleanMerchant.toLowerCase().includes(q)
    );
  }, [rows, filter]);

  const duplicateCount = rows.filter((r) => r.isDuplicate).length;
  const total = rows.reduce((sum, r) => sum + (Number(r.amount) || 0), 0);
  const invalidCount = rows.filter((r) => !r.date || !r.cleanMerchant.trim() || !(r.amount > 0)).length;

  const handleSave = async () => {
    try {
      await batchSave(rows.map((r) => ({
        date: r.date,
        rawDescription: r.rawDescription,
        cleanMerchant: r.cleanMerchant.trim(),
        amount: r.amount,
        category: r.category,
        sourceType: 'CSV',
      }))).unwrap();
      setSavedCount(rows.length);
      setLastSavedFile(file);
      reset();
      toast.success(`${rows.length} transaction${rows.length !== 1 ? 's' : ''} saved`);
    } catch (err: any) {
      toast.error(apiErrorMessage(err, 'Failed to save transactions'));
    }
  };

  /* ── Saved state ── */
  if (savedCount !== null && rows.length === 0 && !isParsing && !error) {
    return (
      <div className="flex flex-col items-center gap-4 rounded-[16px] border border-border-default bg-bg-surface px-6 py-16 text-center">
        <div className="flex size-14 items-center justify-center rounded-full bg-brand-primary-soft">
          <Check className="size-6 text-brand-primary" />
        </div>
        <div>
          <p className="text-xl font-semibold text-text-default">
            <span className="tabular-nums">{savedCount}</span> transaction{savedCount !== 1 ? 's' : ''} saved
          </p>
          <p className="mt-1 text-sm text-text-muted">They're already on your dashboard and in your transaction list.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setSavedCount(null)}><Upload className="size-4" />Import another</Button>
          <Button onClick={() => navigate('/transactions')}>View transactions<ArrowRight className="size-4" /></Button>
        </div>
      </div>
    );
  }

  /* ── Empty / parsing / error state ── */
  if (rows.length === 0) {
    return (
      <>
        {error && (
          <ErrorPanel title={error.title} detail={error.detail} actionLabel="Try another file" onAction={() => { reset(); dropzone.open(); }} />
        )}
        {isParsing ? (
          <div className="flex min-h-[420px] flex-col items-center justify-center gap-3 rounded-[16px] border-[1.5px] border-dashed border-border-strong bg-bg-surface">
            <Loader2 className="size-8 animate-spin text-brand-primary" />
            <p className="text-base font-medium text-text-default">Parsing {file?.name}…</p>
            <p className="text-sm text-text-muted">Categorizing and checking for duplicates</p>
          </div>
        ) : (
          <DropArea
            rootProps={dropzone.getRootProps()}
            inputProps={dropzone.getInputProps()}
            isDragActive={dropzone.isDragActive}
            icon={Upload}
            title="Drop your bank CSV here"
            subtitle="or click anywhere in this area to browse"
            browseLabel="Browse files"
            helper="Supported: CSV files from your bank · Maximum 10MB"
            onBrowse={dropzone.open}
          />
        )}

        {lastSavedFile && !isParsing && (
          <div className="flex items-center justify-center gap-2 text-sm text-text-muted">
            <span>Imported <span className="font-medium text-text-default">{lastSavedFile.name}</span> earlier.</span>
            <Button variant="ghost" size="sm" onClick={() => parseFile(lastSavedFile)}>
              <RotateCcw className="size-3.5" />Quick scan again
            </Button>
          </div>
        )}

        <div className="grid gap-4 md:grid-cols-3">
          {[
            ['Export from your bank', 'Download a CSV statement from your banking app or website.'],
            ['Review the preview', 'We parse, categorize and flag duplicates. Fix anything inline.'],
            ['Save and see it', 'New transactions show up on your dashboard immediately.'],
          ].map(([title, body], i) => (
            <div key={title} className="flex gap-3 rounded-[12px] border border-border-default p-4">
              <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-bg-muted text-xs font-medium text-text-default">{i + 1}</span>
              <div>
                <p className="text-sm font-medium text-text-default">{title}</p>
                <p className="mt-1 text-sm text-text-muted">{body}</p>
              </div>
            </div>
          ))}
        </div>
      </>
    );
  }

  /* ── Preview state ── */
  return (
    <>
      {/* File card (collapsed dropzone) */}
      <div
        {...dropzone.getRootProps()}
        className={cn(
          'flex flex-wrap items-center gap-3.5 rounded-[12px] border-[1.5px] border-dashed bg-bg-surface px-4 py-3.5',
          dropzone.isDragActive ? 'border-brand-primary' : 'border-border-strong'
        )}
      >
        <input {...dropzone.getInputProps()} />
        <div className="flex size-10 shrink-0 items-center justify-center rounded-[10px] bg-brand-primary-soft">
          <FileSpreadsheet className="size-5 text-brand-primary" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-text-default">{file?.name}</p>
          <p className="text-xs font-medium text-text-muted">
            {file ? formatFileSize(file.size) : ''}
            {parseTimeMs !== null && <> · Parsed {rows.length} rows in {(parseTimeMs / 1000).toFixed(1)}s</>}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Chip tone="success" icon={Check}><span className="tabular-nums">{rows.length}</span> parsed</Chip>
          {duplicateCount > 0 && (
            <Chip tone="warning" icon={AlertTriangle}>
              <span className="tabular-nums">{duplicateCount}</span> possible duplicate{duplicateCount !== 1 ? 's' : ''}
            </Chip>
          )}
        </div>
        <Button variant="secondary" onClick={dropzone.open}><Upload className="size-4" />Choose another file</Button>
      </div>

      {/* Review grid */}
      <div className="overflow-hidden rounded-[12px] border border-border-default bg-bg-surface">
        <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div>
            <p className="text-base font-semibold text-text-default">Review before saving</p>
            <p className="text-xs font-medium text-text-muted">Click any cell to edit. Remove rows you don't want to import.</p>
          </div>
          <div className="relative w-56">
            <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-text-muted" />
            <Input placeholder="Filter rows" value={filter} onChange={(e) => setFilter(e.target.value)} className="pl-8" />
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-bg-muted">
              <tr>
                <th className="th-base h-9 w-[150px]">Date</th>
                <th className="th-base h-9">Description</th>
                <th className="th-base h-9 w-[190px]">Merchant</th>
                <th className="th-base h-9 w-[200px]">Category</th>
                <th className="th-base h-9 w-[140px] text-right">Amount (₫)</th>
                <th className="th-base h-9 w-[48px]" />
              </tr>
            </thead>
            <tbody>
              {visibleRows.length === 0 ? (
                <tr><td colSpan={6} className="td-base py-10 text-center text-text-muted">No rows match “{filter}”.</td></tr>
              ) : visibleRows.map((row) => {
                const cellInput = 'h-8 border-transparent bg-transparent px-2 hover:border-border-default focus-visible:bg-bg-surface';
                return (
                  <tr
                    key={row.rowIndex}
                    className={cn('border-t border-border-default', row.isDuplicate && 'bg-status-warning-soft')}
                  >
                    <td className="td-base py-1.5 pr-1">
                      <div className="flex items-center gap-1.5">
                        {row.isDuplicate && (
                          <span title="Possible duplicate of a saved transaction">
                            <AlertTriangle className="size-3.5 shrink-0 text-status-warning" />
                          </span>
                        )}
                        <Input
                          type="date"
                          aria-label="Date"
                          value={row.date}
                          onChange={(e) => updateRow(row.rowIndex, { date: e.target.value })}
                          aria-invalid={!row.date}
                          className={cn(cellInput, 'text-mono')}
                        />
                      </div>
                    </td>
                    <td className="td-base py-1.5 px-2">
                      <Input
                        aria-label="Description"
                        value={row.rawDescription}
                        onChange={(e) => updateRow(row.rowIndex, { rawDescription: e.target.value })}
                        className={cn(cellInput, 'text-text-muted')}
                      />
                    </td>
                    <td className="td-base py-1.5 px-2">
                      <Input
                        aria-label="Merchant"
                        placeholder="Add merchant"
                        value={row.cleanMerchant}
                        onChange={(e) => updateRow(row.rowIndex, { cleanMerchant: e.target.value })}
                        aria-invalid={!row.cleanMerchant.trim()}
                        className={cn(cellInput, 'font-medium')}
                      />
                    </td>
                    <td className="td-base py-1.5 px-2">
                      <CategorySelect
                        value={row.category}
                        categories={categories}
                        onChange={(v) => updateRow(row.rowIndex, { category: v })}
                        className="h-8"
                      />
                    </td>
                    <td className="td-base py-1.5 px-2">
                      <Input
                        type="number"
                        inputMode="numeric"
                        min={0}
                        step={1}
                        aria-label="Amount"
                        value={row.amount}
                        onChange={(e) => updateRow(row.rowIndex, { amount: Math.abs(Number(e.target.value) || 0) })}
                        aria-invalid={!(row.amount > 0)}
                        className={cn(cellInput, 'text-mono text-right')}
                      />
                    </td>
                    <td className="td-base py-1.5 pl-0 pr-2">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label="Remove row"
                        className="hover:text-status-danger"
                        onClick={() => removeRow(row.rowIndex)}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      <SaveBar
        title={<><span className="tabular-nums">{rows.length}</span> transaction{rows.length !== 1 ? 's' : ''} ready · <span className="text-mono">{formatCurrencyFull(total)}</span> total</>}
        subtitle={
          invalidCount > 0
            ? `${invalidCount} row${invalidCount !== 1 ? 's need' : ' needs'} a date, merchant and amount before saving`
            : duplicateCount > 0
              ? `${duplicateCount} row${duplicateCount !== 1 ? 's' : ''} flagged as possible duplicates — remove them or save anyway`
              : 'Saved transactions show up on your dashboard right away'
        }
        subtitleTone={invalidCount > 0 || duplicateCount > 0 ? 'warning' : 'muted'}
      >
        <Button variant="ghost" onClick={reset} disabled={isSaving}>Discard</Button>
        <Button onClick={handleSave} disabled={isSaving || invalidCount > 0}>
          {isSaving
            ? <><Loader2 className="size-4 animate-spin" />Saving…</>
            : <><Check className="size-4" />Save {rows.length} transaction{rows.length !== 1 ? 's' : ''}</>}
        </Button>
      </SaveBar>
    </>
  );
}

/* ───────────────────────── Receipt tab ───────────────────────── */

function ReceiptScan({ categories }: { categories: string[] }) {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [result, setResult] = useState<OcrResult | null>(null);
  const [form, setForm] = useState<ReceiptForm | null>(null);
  const [edited, setEdited] = useState<Partial<Record<keyof ReceiptForm, boolean>>>({});
  const [readTimeMs, setReadTimeMs] = useState<number | null>(null);
  const [error, setError] = useState<{ title: string; detail?: string } | null>(null);

  const [processOcr, { isLoading: isReading }] = useProcessOcrMutation();
  const [batchSave, { isLoading: isSaving }] = useBatchSaveTransactionsMutation();

  // Object URL for the image preview — revoked when the file changes or the page unmounts.
  useEffect(() => {
    if (!file || !file.type.startsWith('image/')) { setPreviewUrl(null); return; }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const reset = () => {
    setFile(null);
    setResult(null);
    setForm(null);
    setEdited({});
    setError(null);
    setReadTimeMs(null);
  };

  const readReceipt = useCallback(async (f: File) => {
    setFile(f);
    setResult(null);
    setForm(null);
    setEdited({});
    setError(null);
    const formData = new FormData();
    formData.append('image', f);
    const started = performance.now();
    try {
      const data: OcrResult = await processOcr(formData).unwrap();
      setReadTimeMs(performance.now() - started);
      const normalized: OcrResult = { ...data, lineItems: data?.lineItems || [] };
      setResult(normalized);
      setForm({
        merchant: normalized.merchant && normalized.merchant !== 'UNKNOWN MERCHANT' ? normalized.merchant : '',
        date: normalized.date ? formatDateISO(normalized.date) : todayISO(),
        amount: Math.abs(Number(normalized.totalAmount) || 0),
        category: normalized.category || 'Other',
      });
    } catch (err: any) {
      setError({
        title: 'Could not read this receipt. Try a clearer photo.',
        detail: apiErrorMessage(err, ''),
      });
    }
  }, [processOcr]);

  const onDrop = useCallback((accepted: File[], rejected: FileRejection[]) => {
    if (rejected.length) {
      setError({ title: 'Could not use this file.', detail: rejectionMessage(rejected, 'a JPG, PNG or PDF') });
      return;
    }
    if (accepted[0]) readReceipt(accepted[0]);
  }, [readReceipt]);

  const dropzone = useDropzone({
    onDrop,
    accept: {
      'image/jpeg': ['.jpg', '.jpeg'],
      'image/png': ['.png'],
      'application/pdf': ['.pdf'],
    },
    maxFiles: 1,
    maxSize: MAX_FILE_SIZE,
    noClick: !!file,
    noKeyboard: !!file,
  });

  const setField = <K extends keyof ReceiptForm>(key: K, value: ReceiptForm[K]) => {
    setForm((f) => (f ? { ...f, [key]: value } : f));
    setEdited((e) => ({ ...e, [key]: true }));
  };

  const handleSave = async () => {
    if (!form) return;
    try {
      await batchSave([{
        date: form.date,
        rawDescription: form.merchant.trim(),
        cleanMerchant: form.merchant.trim(),
        amount: form.amount,
        category: form.category,
        sourceType: 'OCR',
      }]).unwrap();
      toast.success('Receipt saved as a transaction', {
        action: { label: 'View', onClick: () => navigate('/transactions') },
      });
      reset();
    } catch (err: any) {
      toast.error(apiErrorMessage(err, 'Failed to save transaction'));
    }
  };

  /* ── Empty / error state ── */
  if (!file || (!isReading && !result)) {
    return (
      <>
        {error && (
          <ErrorPanel title={error.title} detail={error.detail} actionLabel="Try another" onAction={() => { reset(); dropzone.open(); }} />
        )}
        <DropArea
          rootProps={dropzone.getRootProps()}
          inputProps={dropzone.getInputProps()}
          isDragActive={dropzone.isDragActive}
          icon={Camera}
          title="Drop a receipt photo here"
          subtitle="or click anywhere in this area to browse"
          browseLabel="Browse files"
          helper="Supported: JPG, PNG or PDF · Maximum 10MB"
          onBrowse={dropzone.open}
        />
        <p className="flex items-center justify-center gap-2 text-xs font-medium text-text-muted">
          <ShieldCheck className="size-4" />
          The image is discarded after reading — only the details you save are kept.
        </p>
      </>
    );
  }

  const lineItems = result?.lineItems || [];
  const itemsTotal = lineItems.reduce((sum, li) => sum + (Number(li.price) || 0), 0);
  const itemsMatch = form ? Math.round(itemsTotal) === Math.round(form.amount) : false;
  const canSave = !!form && !!form.merchant.trim() && !!form.date && form.amount > 0 && !!form.category;

  const autoMark = (key: keyof ReceiptForm) => (
    <span className={cn('text-xs font-medium', edited[key] ? 'text-text-muted' : 'text-brand-primary')}>
      {edited[key] ? 'Edited' : 'Auto-detected'}
    </span>
  );

  /* ── Reading / result state (two columns) ── */
  return (
    <>
      <div {...dropzone.getRootProps()} className="grid flex-1 gap-5 lg:grid-cols-[420px_1fr]">
        <input {...dropzone.getInputProps()} />

        {/* Left: image */}
        <div className="flex flex-col gap-3.5 rounded-[12px] border border-border-default bg-bg-surface p-5">
          <div className="relative flex min-h-[360px] flex-1 items-center justify-center overflow-hidden rounded-[10px] bg-bg-muted">
            {previewUrl ? (
              <img src={previewUrl} alt="Receipt preview" className="max-h-[560px] w-full object-contain" />
            ) : (
              <div className="flex flex-col items-center gap-2 text-text-muted">
                <FileText className="size-10" />
                <span className="text-sm">PDF receipt</span>
              </div>
            )}
            {isReading && (
              <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-bg-surface/70 backdrop-blur-[1px]">
                <Loader2 className="size-7 animate-spin text-brand-primary" />
                <p className="text-sm font-medium text-text-default">Reading receipt…</p>
              </div>
            )}
          </div>
          <div className="flex items-center gap-2.5">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-text-default">{file.name}</p>
              <p className="text-xs font-medium text-text-muted">
                {formatFileSize(file.size)}
                {readTimeMs !== null && <> · Read in {(readTimeMs / 1000).toFixed(1)}s</>}
              </p>
            </div>
            <Button variant="secondary" onClick={dropzone.open} disabled={isReading}><Camera className="size-4" />Replace</Button>
          </div>
          <div className="flex items-start gap-2 rounded-[8px] bg-bg-muted px-2.5 py-2">
            <ShieldCheck className="mt-0.5 size-3.5 shrink-0 text-text-muted" />
            <p className="text-xs font-medium text-text-muted">The image is discarded after reading — only the details are saved.</p>
          </div>
        </div>

        {/* Right: extracted details */}
        <div className="flex flex-col gap-[18px] rounded-[12px] border border-border-default bg-bg-surface p-5">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-base font-semibold text-text-default">Receipt details</p>
              <p className="text-xs font-medium text-text-muted">Check what we read, fix anything that's off, then save.</p>
            </div>
            {result && <Chip tone="success" icon={Check}>Receipt read</Chip>}
          </div>

          {isReading || !form ? (
            <div className="flex flex-col gap-4">
              <div className="grid gap-3 sm:grid-cols-2">
                {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-[60px] w-full" />)}
              </div>
              <Skeleton className="h-48 w-full" />
            </div>
          ) : (
            <>
              {result?.isDuplicate && (
                <div className="flex items-center gap-2.5 rounded-[8px] bg-status-warning-soft px-3 py-2.5">
                  <AlertTriangle className="size-4 shrink-0 text-status-warning" />
                  <div className="flex-1">
                    <p className="text-sm font-medium text-status-warning">Possible duplicate</p>
                    <p className="text-xs text-text-default">
                      A <span className="text-mono">{formatCurrencyFull(form.amount)}</span> transaction around this date is already saved.
                    </p>
                  </div>
                  <Button variant="ghost" size="sm" onClick={(e) => { e.stopPropagation(); navigate('/transactions'); }}>View</Button>
                </div>
              )}

              <div className="grid gap-3 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center gap-1.5"><Label htmlFor="r-merchant" className="text-sm font-medium text-text-default">Merchant</Label>{autoMark('merchant')}</div>
                  <Input id="r-merchant" placeholder="Add merchant" value={form.merchant} aria-invalid={!form.merchant.trim()} onChange={(e) => setField('merchant', e.target.value)} />
                </div>
                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center gap-1.5"><Label htmlFor="r-date" className="text-sm font-medium text-text-default">Date</Label>{autoMark('date')}</div>
                  <Input id="r-date" type="date" value={form.date} aria-invalid={!form.date} onChange={(e) => setField('date', e.target.value)} className="text-mono" />
                </div>
                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center gap-1.5"><Label htmlFor="r-total" className="text-sm font-medium text-text-default">Total (₫)</Label>{autoMark('amount')}</div>
                  <Input id="r-total" type="number" inputMode="numeric" min={0} step={1} value={form.amount} aria-invalid={!(form.amount > 0)} onChange={(e) => setField('amount', Math.abs(Number(e.target.value) || 0))} className="text-mono" />
                </div>
                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center gap-1.5"><Label className="text-sm font-medium text-text-default">Category</Label>{autoMark('category')}</div>
                  <CategorySelect value={form.category} categories={categories} onChange={(v) => setField('category', v)} />
                </div>
              </div>

              <div>
                <Table className="text-sm">
                  <TableHeader>
                    <TableRow className="bg-bg-muted hover:bg-bg-muted">
                      <TableHead className="h-[34px]">Line items ({lineItems.length})</TableHead>
                      <TableHead className="h-[34px] text-right">Price (₫)</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {lineItems.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={2} className="py-6 text-center text-text-muted">
                          No line items detected — the total above is what gets saved.
                        </TableCell>
                      </TableRow>
                    ) : (
                      <>
                        {lineItems.map((li, i) => (
                          <TableRow key={i}>
                            <TableCell className="text-text-default">{li.item}</TableCell>
                            <TableCell className="text-mono text-right text-text-default">{new Intl.NumberFormat('en-US').format(Number(li.price) || 0)}</TableCell>
                          </TableRow>
                        ))}
                        <TableRow className="hover:bg-transparent">
                          <TableCell className={cn('text-xs font-medium', itemsMatch ? 'text-brand-primary' : 'text-status-warning')}>
                            <span className="flex items-center gap-1.5">
                              {itemsMatch
                                ? <><Check className="size-3.5" />Items add up to total</>
                                : <><AlertTriangle className="size-3.5" />Items add up to {formatCurrencyFull(itemsTotal)} — check the total</>}
                            </span>
                          </TableCell>
                          <TableCell className="text-mono text-right font-semibold text-text-default">{new Intl.NumberFormat('en-US').format(itemsTotal)}</TableCell>
                        </TableRow>
                      </>
                    )}
                  </TableBody>
                </Table>
              </div>
            </>
          )}
        </div>
      </div>

      <SaveBar
        title={form
          ? <>1 transaction · {form.merchant.trim() || 'No merchant'} · <span className="text-mono">{formatCurrencyFull(form.amount)}</span></>
          : 'Reading receipt…'}
        subtitle={form && !canSave ? 'Add a merchant, date and total before saving' : 'Saved as a single transaction with source “Receipt”'}
        subtitleTone={form && !canSave ? 'warning' : 'muted'}
      >
        <Button variant="secondary" onClick={() => { reset(); dropzone.open(); }} disabled={isReading || isSaving}>
          <Camera className="size-4" />Scan another
        </Button>
        <Button onClick={handleSave} disabled={!canSave || isReading || isSaving}>
          {isSaving ? <><Loader2 className="size-4 animate-spin" />Saving…</> : <><Check className="size-4" />Save as transaction</>}
        </Button>
      </SaveBar>
    </>
  );
}
