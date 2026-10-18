import { isValidAmount, isValidIsoDate, type ParsedTransaction, type TransactionRequest } from '@finance-tracker/shared';

/** A row can be saved once it has a date, a merchant and an amount. */
export function isComplete(row: ParsedTransaction): boolean {
  return isValidIsoDate(row.date) && !!row.cleanMerchant.trim() && isValidAmount(row.amount === null ? null : Math.abs(row.amount));
}

/** Rows the user kept, as save requests. Amounts are stored positive. */
export function rowsToTransactions(rows: ParsedTransaction[], included: ReadonlySet<number>): TransactionRequest[] {
  return rows
    .filter((row) => included.has(row.rowIndex) && isComplete(row))
    .map((row) => ({
      date: row.date as string,
      rawDescription: row.rawDescription?.trim() || row.cleanMerchant.trim(),
      cleanMerchant: row.cleanMerchant.trim(),
      amount: Math.abs(row.amount as number),
      category: row.category,
      sourceType: 'CSV',
    }));
}

/** By default keep every complete row that isn't a possible duplicate. */
export function defaultSelection(rows: ParsedTransaction[]): Set<number> {
  return new Set(rows.filter((row) => isComplete(row) && !row.isDuplicate).map((row) => row.rowIndex));
}
