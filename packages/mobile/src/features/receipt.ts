import {
  isValidAmount,
  isValidIsoDate,
  todayIso,
  type ParsedReceipt,
  type TransactionRequest,
} from '@finance-tracker/shared';

/** What the user reviews after a scan; amount kept as typed text until saving. */
export interface ReceiptForm {
  merchant: string;
  date: string;
  amount: string;
  categoryId: string;
}

/** Same wording as the web app's receipt warnings. */
export const RECEIPT_WARNINGS: Record<string, string> = {
  MERCHANT_NOT_FOUND: "Couldn't find the shop name.",
  DATE_NOT_FOUND: "Couldn't find a date, so today's date was used.",
  DATE_SUSPICIOUS: "The date on the receipt didn't look right, so today's date was used.",
  TOTAL_NOT_FOUND: "Couldn't find the total.",
  TOTAL_GUESSED: 'The total was taken from the largest amount near the bottom. Double-check it.',
  ITEMS_DO_NOT_MATCH_TOTAL: "The items don't add up to the total.",
};

export function formFromReceipt(receipt: ParsedReceipt, today: string = todayIso()): ReceiptForm {
  return {
    merchant: receipt.merchant ?? '',
    date: receipt.date ?? today,
    amount: receipt.totalAmount != null ? String(Math.abs(receipt.totalAmount)) : '',
    categoryId: receipt.categoryId,
  };
}

/** VND has no decimals: keep the digits only ("90.000 ₫" → 90000). */
export function parseAmountInput(text: string): number | null {
  const digits = text.replace(/\D/g, '');
  return digits ? Number(digits) : null;
}

/** What still needs fixing before the receipt can be saved; empty when it's ready. */
export function receiptProblems(form: ReceiptForm): string[] {
  const problems: string[] = [];
  if (!form.merchant.trim()) problems.push('Add the shop name');
  if (!isValidIsoDate(form.date)) problems.push('Use a date like 2026-10-17');
  if (!isValidAmount(parseAmountInput(form.amount))) problems.push('Enter the total');
  return problems;
}

/** The request that saves a reviewed receipt, with its line items. */
export function receiptToTransaction(form: ReceiptForm, receipt: ParsedReceipt): TransactionRequest {
  const merchant = form.merchant.trim();
  return {
    date: form.date,
    rawDescription: merchant,
    cleanMerchant: merchant,
    amount: parseAmountInput(form.amount) ?? 0,
    categoryId: form.categoryId,
    sourceType: 'OCR',
    lineItems: receipt.lineItems
      .filter((line) => line.item.trim() && isValidAmount(Math.abs(line.price)))
      .map((line) => ({ item: line.item.trim(), price: Math.abs(line.price) })),
  };
}
