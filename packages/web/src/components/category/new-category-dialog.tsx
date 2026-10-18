import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import type { Category, CategoryKind } from '@finance-tracker/shared';
import { useCreateCategoryMutation } from '@/store/api/category-api';
import { apiErrorMessage } from '@/lib/api-error';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { cn } from 'cn';
import { CATEGORY_COLORS } from './category-colors';

const KINDS: { kind: CategoryKind; label: string; hint: string }[] = [
  { kind: 'EXPENSE', label: 'Spending', hint: 'Counts toward spending and budgets' },
  { kind: 'INCOME', label: 'Income', hint: 'Money you receive' },
  { kind: 'TRANSFER', label: 'Transfer', hint: 'Between your own accounts; left out of totals' },
];

/** Name, kind and color for one of the user's own categories. */
export function NewCategoryDialog({ open, onOpenChange, onCreated }: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated?: (category: Category) => void;
}) {
  const [name, setName] = useState('');
  const [kind, setKind] = useState<CategoryKind>('EXPENSE');
  const [color, setColor] = useState(CATEGORY_COLORS[4]);
  const [create, { isLoading }] = useCreateCategoryMutation();

  useEffect(() => {
    if (open) { setName(''); setKind('EXPENSE'); setColor(CATEGORY_COLORS[4]); }
  }, [open]);

  const submit = async () => {
    try {
      const created = await create({ name: name.trim(), kind, color }).unwrap();
      toast.success(`Category "${created.name}" created`);
      onOpenChange(false);
      onCreated?.(created);
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not create the category'));
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[420px]">
        <DialogHeader>
          <DialogTitle>New category</DialogTitle>
          <DialogDescription>Only you see it. You can rename or archive it later in Settings.</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); if (name.trim()) submit(); }}>
          <div className="space-y-1.5">
            <Label htmlFor="new-category-name" className="field-label">Name</Label>
            <Input id="new-category-name" autoFocus maxLength={100} value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Pets" />
          </div>
          <div className="space-y-1.5">
            <Label className="field-label">Counts as</Label>
            <div className="grid grid-cols-3 gap-2">
              {KINDS.map((k) => (
                <button
                  key={k.kind}
                  type="button"
                  title={k.hint}
                  onClick={() => setKind(k.kind)}
                  className={cn(
                    'rounded-lg border px-2 py-2 text-sm font-medium transition-colors',
                    kind === k.kind ? 'border-brand-primary bg-brand-primary-soft text-brand-primary' : 'border-border-default text-text-muted hover:bg-bg-muted'
                  )}
                >
                  {k.label}
                </button>
              ))}
            </div>
            <p className="text-xs text-text-muted">{KINDS.find((k) => k.kind === kind)?.hint}</p>
          </div>
          <div className="space-y-1.5">
            <Label className="field-label">Color</Label>
            <ColorPicker value={color} onChange={setColor} />
          </div>
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>Cancel</Button>
            <Button type="submit" disabled={!name.trim() || isLoading}>
              {isLoading && <Loader2 className="size-4 animate-spin" />}Create
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function ColorPicker({ value, onChange }: { value: string; onChange: (color: string) => void }) {
  return (
    <div className="flex flex-wrap gap-2">
      {CATEGORY_COLORS.map((c) => (
        <button
          key={c}
          type="button"
          aria-label={`Color ${c}`}
          onClick={() => onChange(c)}
          className={cn('size-6 rounded-full ring-offset-2 ring-offset-bg-surface transition', value === c && 'ring-2 ring-text-default')}
          style={{ backgroundColor: c }}
        />
      ))}
    </div>
  );
}
