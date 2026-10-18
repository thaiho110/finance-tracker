import { useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { humanize, useGetAuditQuery } from '@/store/api/admin-api';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const PAGE_SIZE = 50;

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/** Every administrative change, newest first. */
export default function AdminAuditPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useGetAuditQuery({ page, size: PAGE_SIZE });
  const entries = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="page-title">Audit log</h1>
        <p className="text-sm text-text-muted">Who changed accounts, roles and features, and when.</p>
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[170px]">When</TableHead>
            <TableHead className="w-[220px]">Who</TableHead>
            <TableHead className="w-[190px]">What</TableHead>
            <TableHead className="w-[220px]">Target</TableHead>
            <TableHead>Details</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading && Array.from({ length: 6 }).map((_, i) => (
            <TableRow key={i}><TableCell colSpan={5}><Skeleton className="h-5 w-full" /></TableCell></TableRow>
          ))}
          {!isLoading && entries.length === 0 && (
            <TableRow><TableCell colSpan={5} className="py-10 text-center text-text-muted">Nothing has been changed yet</TableCell></TableRow>
          )}
          {entries.map((entry) => (
            <TableRow key={entry.id}>
              <TableCell className="tabular-nums text-text-muted">{formatDateTime(entry.createdAt)}</TableCell>
              <TableCell>{entry.actorId ? (entry.actorEmail ?? 'Deleted account') : <span className="text-text-muted">System</span>}</TableCell>
              <TableCell className="font-medium">{humanize(entry.action)}</TableCell>
              <TableCell>{entry.target ?? '—'}</TableCell>
              <TableCell className="text-text-muted">{entry.details ?? ''}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 text-sm text-text-muted">
          <span>Page {page + 1} of {totalPages}</span>
          <Button variant="secondary" size="icon-sm" disabled={page === 0} onClick={() => setPage(page - 1)} aria-label="Previous page">
            <ChevronLeft className="size-4" />
          </Button>
          <Button variant="secondary" size="icon-sm" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)} aria-label="Next page">
            <ChevronRight className="size-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
