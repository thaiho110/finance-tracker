/**
 * API contract types mirroring the backend DTOs (/api/v1). Kept by hand so every client
 * (web, desktop, mobile) builds without a running backend; `pnpm gen:types` writes the
 * OpenAPI-generated equivalents to `generated/api.ts` for cross-checking.
 *
 * Dates are ISO strings (`2026-10-17`), timestamps ISO date-times, amounts numbers in VND.
 */

export type ClientId = 'finance-tracker-web' | 'finance-tracker-desktop' | 'finance-tracker-mobile';

export type SourceType = 'CSV' | 'OCR' | 'MANUAL';

/** Spring's ProblemDetail body for errors. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  path?: string;
  /** Validation errors as "field: message". */
  errors?: string[];
}

/* ───────── auth ───────── */

export interface AuthResponse {
  token: string;
  refreshToken: string;
  id: string;
  email: string;
  role: string;
  permissions: string[];
}

export interface UserInfo {
  id: string;
  email: string;
  role: string;
  permissions: string[];
}

export interface Credentials {
  email: string;
  password: string;
}

/* ───────── transactions ───────── */

export interface ReceiptItem {
  id: string;
  itemDescription: string;
  price: number;
}

export interface Transaction {
  id: string;
  date: string;
  rawDescription: string;
  cleanMerchant: string;
  amount: number;
  category: string;
  sourceType: SourceType;
  clientId: string | null;
  isDuplicate: boolean;
  createdAt: string | null;
  receiptItems: ReceiptItem[];
}

export interface ReceiptLineItem {
  item: string;
  price: number;
}

/** A transaction to save (after review). Line items are kept only for OCR. */
export interface TransactionRequest {
  date: string;
  rawDescription: string;
  cleanMerchant: string;
  amount: number;
  category: string;
  sourceType: SourceType;
  lineItems?: ReceiptLineItem[];
}

/** Corrections to a saved transaction; where it came from can't change. */
export interface TransactionUpdateRequest {
  date: string;
  rawDescription?: string;
  cleanMerchant: string;
  amount: number;
  category: string;
}

export interface TransactionFilter {
  page?: number;
  size?: number;
  sort?: string;
  category?: string;
  dateFrom?: string;
  dateTo?: string;
}

export interface TransactionSummary {
  totalExpenses: number;
  totalIncome: number;
  transactionCount: number;
  averageTransaction: number;
  categoryBreakdown: { category: string; total: number }[];
  monthlyTrend: { month: string; total: number }[];
  recentTransactions: Transaction[];
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}

/* ───────── imports ───────── */

/** A CSV row drafted for review; null fields couldn't be read from the file. */
export interface ParsedTransaction {
  rowIndex: number;
  date: string | null;
  rawDescription: string | null;
  cleanMerchant: string;
  amount: number | null;
  category: string;
  isDuplicate: boolean;
}

export interface ParsedReceipt {
  merchant: string | null;
  date: string | null;
  totalAmount: number | null;
  category: string;
  isDuplicate: boolean;
  lineItems: ReceiptLineItem[];
  /** Codes such as TOTAL_GUESSED, DATE_NOT_FOUND. */
  warnings: string[];
}
