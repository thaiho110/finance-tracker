import type { ParsedReceipt, ParsedTransaction } from '@finance-tracker/shared';
import { formFromReceipt, parseAmountInput, receiptProblems, receiptToTransaction } from '../receipt';
import { defaultSelection, rowsToTransactions } from '../csv';

const receipt: ParsedReceipt = {
  merchant: 'Bach Hoa Xanh',
  date: null,
  totalAmount: 90000,
  categoryId: 'cat-groceries',
  category: 'Groceries',
  isDuplicate: false,
  lineItems: [{ item: 'RAU MUONG ', price: 12000 }, { item: '', price: 5 }],
  warnings: ['DATE_NOT_FOUND'],
};

describe('receipt review', () => {
  it("fills missing fields with today's date", () => {
    expect(formFromReceipt(receipt, '2026-10-18')).toEqual({
      merchant: 'Bach Hoa Xanh', date: '2026-10-18', amount: '90000', categoryId: 'cat-groceries',
    });
  });

  it('reads VND as digits only', () => {
    expect(parseAmountInput('90.000 ₫')).toBe(90000);
    expect(parseAmountInput('')).toBeNull();
  });

  it('explains what blocks saving', () => {
    expect(receiptProblems({ merchant: ' ', date: '2026-02-31', amount: '', categoryId: 'cat-other' }))
      .toEqual(['Add the shop name', 'Use a date like 2026-10-17', 'Enter the total']);
  });

  it('saves an OCR transaction with the corrected total and clean line items', () => {
    const form = { merchant: ' Bach Hoa Xanh ', date: '2026-10-17', amount: '95.000', categoryId: 'cat-groceries' };
    expect(receiptToTransaction(form, receipt)).toEqual({
      date: '2026-10-17',
      rawDescription: 'Bach Hoa Xanh',
      cleanMerchant: 'Bach Hoa Xanh',
      amount: 95000,
      categoryId: 'cat-groceries',
      sourceType: 'OCR',
      lineItems: [{ item: 'RAU MUONG', price: 12000 }],
    });
  });
});

describe('csv review', () => {
  const row = (rowIndex: number, overrides: Partial<ParsedTransaction> = {}): ParsedTransaction => ({
    rowIndex, date: '2026-09-05', rawDescription: 'GRAB', cleanMerchant: 'GRAB', amount: -35000,
    categoryId: 'cat-transport', category: 'Transportation', isDuplicate: false, ...overrides,
  });

  it('keeps complete, non-duplicate rows by default', () => {
    const rows = [row(0), row(1, { isDuplicate: true }), row(2, { date: null })];
    expect([...defaultSelection(rows)]).toEqual([0]);
  });

  it('saves selected rows with positive amounts', () => {
    expect(rowsToTransactions([row(0), row(1)], new Set([1]))).toEqual([{
      date: '2026-09-05', rawDescription: 'GRAB', cleanMerchant: 'GRAB', amount: 35000,
      categoryId: 'cat-transport', sourceType: 'CSV',
    }]);
  });
});
