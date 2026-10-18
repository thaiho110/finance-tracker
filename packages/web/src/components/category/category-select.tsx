import { useMemo, useState } from 'react';
import { Plus } from 'lucide-react';
import type { Category, CategoryKind } from '@finance-tracker/shared';
import { CategoryBadge } from '@/components/ui/category-badge';
import {
  Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectSeparator, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { useCan } from '@/lib/permissions';
import { cn } from 'cn';
import { NewCategoryDialog } from './new-category-dialog';

const NEW = '__new__';
const GROUPS: { kind: CategoryKind; label: string }[] = [
  { kind: 'EXPENSE', label: 'Spending' },
  { kind: 'INCOME', label: 'Income' },
  { kind: 'TRANSFER', label: 'Transfers' },
];

/** A category to show for the current value even if it isn't pickable any more (archived, hidden). */
export interface CurrentCategory {
  id: string;
  name: string;
  color?: string | null;
}

/**
 * Category picker (ADR-015): grouped by kind, colored dots, and "+ New category" at the bottom
 * (CATEGORY_WRITE) which creates one and selects it.
 */
export function CategorySelect({ value, categories, onChange, current, className, placeholder = 'Choose category' }: {
  /** Selected category id. */
  value: string;
  categories: Category[];
  onChange: (category: Category) => void;
  current?: CurrentCategory;
  className?: string;
  placeholder?: string;
}) {
  const can = useCan();
  const [creating, setCreating] = useState(false);
  const selected = categories.find((c) => c.id === value);
  const shown = selected ?? (current && current.id === value ? current : undefined);
  const groups = useMemo(
    () => GROUPS.map((g) => ({ ...g, items: categories.filter((c) => c.kind === g.kind) })).filter((g) => g.items.length),
    [categories]
  );

  return (
    <>
      <Select
        value={value}
        onValueChange={(id) => {
          if (id === NEW) { setCreating(true); return; }
          const picked = categories.find((c) => c.id === id);
          if (picked) onChange(picked);
        }}
      >
        <SelectTrigger className={cn('h-9 w-full bg-bg-surface', className)}>
          <SelectValue placeholder={placeholder}>
            {shown ? <CategoryBadge category={shown.name} color={shown.color} /> : null}
          </SelectValue>
        </SelectTrigger>
        <SelectContent>
          {shown && !selected && (
            <SelectItem value={shown.id}><CategoryBadge category={shown.name} color={shown.color} /></SelectItem>
          )}
          {groups.map((group, i) => (
            <SelectGroup key={group.kind}>
              {i > 0 && <SelectSeparator />}
              {groups.length > 1 && <SelectLabel>{group.label}</SelectLabel>}
              {group.items.map((c) => (
                <SelectItem key={c.id} value={c.id}><CategoryBadge category={c.name} color={c.color} /></SelectItem>
              ))}
            </SelectGroup>
          ))}
          {can('CATEGORY_WRITE') && (
            <>
              <SelectSeparator />
              <SelectItem value={NEW} className="text-brand-primary">
                <span className="inline-flex items-center gap-1.5 text-sm font-medium"><Plus className="size-3.5" />New category</span>
              </SelectItem>
            </>
          )}
        </SelectContent>
      </Select>
      <NewCategoryDialog open={creating} onOpenChange={setCreating} onCreated={onChange} />
    </>
  );
}
