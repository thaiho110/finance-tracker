import { cn } from '@/lib/utils';
import { formatCurrencyFull } from '@/lib/utils';
import { TrendingUp, TrendingDown } from 'lucide-react';

interface StatCardProps {
  label: string;
  value: number | string;
  delta?: string;
  deltaPositive?: boolean;
  isText?: boolean;
  icon?: React.ReactNode;
}

/**
 * Stat card for the dashboard summary row.
 * Value uses Mono/28. If value is text, switches to Display/28.
 * Delta is a muted caption below the value.
 */
export function StatCard({ label, value, delta, deltaPositive, isText, icon }: StatCardProps) {
  return (
    <div className="flex flex-col gap-1 surface-card p-5">
      <div className="flex items-center justify-between">
        <span className="field-label">{label}</span>
        {icon && <span className="text-text-muted">{icon}</span>}
      </div>
      <div className={cn(
        'text-[28px] font-semibold leading-[36px] tracking-tight text-text-default',
        !isText && 'font-mono'
      )}>
        {typeof value === 'number' ? formatCurrencyFull(value) : value}
      </div>
      {delta && (
        <div className="flex items-center gap-1">
          {deltaPositive !== undefined && (
            deltaPositive
              ? <TrendingUp className="size-3 text-brand-primary" />
              : <TrendingDown className="size-3 text-status-danger" />
          )}
          <span className="text-xs font-medium text-text-muted">{delta}</span>
        </div>
      )}
    </div>
  );
}
