/**
 * Kept — shared formatting utilities.
 * Currency: VND with ₫ prefix. Dates: "Oct 16" in lists, ISO in forms.
 */
export function cn(...args: any[]) {
  return args.filter(Boolean).join(' ');
}

/**
 * Format a number as VND currency.
 * Uses ₫ prefix with comma thousands separators.
 */
export function formatCurrency(amount: number): string {
  const abs = Math.abs(amount);
  if (abs >= 1_000_000) {
    const m = abs / 1_000_000;
    return `₫${m.toFixed(m % 1 === 0 ? 0 : 2)}M`;
  }
  return `₫${new Intl.NumberFormat('en-US').format(abs)}`;
}

/**
 * Format a number as VND currency with full precision (no short form).
 */
export function formatCurrencyFull(amount: number): string {
  const abs = Math.abs(amount);
  return `₫${new Intl.NumberFormat('en-US').format(abs)}`;
}

/**
 * Format a date as "Oct 16" (short, for lists and dashboard).
 */
export function formatDateShort(dateStr: string): string {
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

/**
 * Format a date as ISO "2026-10-16" (for forms and CSV preview).
 */
export function formatDateISO(dateStr: string): string {
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toISOString().slice(0, 10);
}

/**
 * Format a month as "October 2026" (for dashboard header).
 */
export function formatMonthYear(date: Date): string {
  return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
}

/**
 * Category → tone mapping (matches chart colors).
 */
const CATEGORY_TONES: Record<string, string> = {
  'Food & Dining': 'green',
  'Transport': 'teal',
  'Shopping': 'amber',
  'Bills & Utilities': 'coral',
  'Other': 'slate',
  'Uncategorized': 'slate',
  'Entertainment': 'purple',
};

/**
 * Get the CSS tone class for a category badge.
 */
export function getCategoryTone(category: string): string {
  return CATEGORY_TONES[category] || 'slate';
}

/**
 * Chart colors array matching the design tokens.
 */
export const CHART_COLORS = [
  '#1E6B47', // Food & Dining — green
  '#2A9D8F', // Transport — teal
  '#E0A13A', // Shopping — amber
  '#E3704F', // Bills & Utilities — coral
  '#5D6C7B', // Other — slate
  '#8C6BB1', // Entertainment — purple
];

/**
 * Get chart color for a category name.
 */
export function getChartColor(category: string): string {
  const idx = Object.keys(CATEGORY_TONES).indexOf(category);
  return CHART_COLORS[idx >= 0 ? idx : 4]; // default to slate
}
