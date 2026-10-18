import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import type { Category, CategoryChanges, CategoryKind } from '@finance-tracker/shared';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { cn } from 'cn';
import { CATEGORY_COLORS } from './category-colors';
import { ColorPicker } from './new-category-dialog';

export const KIND_LABELS: Record<CategoryKind, string> = { EXPENSE: 'Spending', INCOME: 'Income', TRANSFER: 'Transfer' };

/** Rename, recolor or change the kind of a category. Used for own categories and, by admins, for defaults. */
export function EditCategoryDialog({ category, onClose, onSave, saving }: {
  category: Category | null;
  onClose: () => void;
  onSave: (changes: CategoryChanges) => void;
  saving: boolean;
}) {
  const [name, setName] = useState('');
  const [kind, setKind] = useState<CategoryKind>('EXPENSE');
  const [color, setColor] = useState(CATEGORY_COLORS[4]);
  const isOther = !!category?.system && category.name === 'Other';

  useEffect(() => {
    if (!category) return;
    setName(category.name);
    setKind(category.kind);
    setColor(category.color ?? CATEGORY_COLORS[4]);
  }, [category]);

  return (
    <Dialog open={!!category} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[420px]">
        <DialogHeader>
          <DialogTitle>Edit category</DialogTitle>
          <DialogDescription>Changes apply to every transaction already in this category.</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (!category || !name.trim()) return;
            const changes: CategoryChanges = {};
            if (name.trim() !== category.name) changes.name = name.trim();
            if (kind !== category.kind) changes.kind = kind;
            if (color !== category.color) changes.color = color;
            onSave(changes);
          }}
        >
          <div className="space-y-1.5">
            <Label htmlFor="edit-category-name" className="field-label">Name</Label>
            <Input id="edit-category-name" maxLength={100} value={name} disabled={isOther} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label className="field-label">Counts as</Label>
            <div className="grid grid-cols-3 gap-2">
              {(Object.keys(KIND_LABELS) as CategoryKind[]).map((k) => (
                <button
                  key={k}
                  type="button"
                  disabled={isOther}
                  onClick={() => setKind(k)}
                  className={cn(
                    'rounded-lg border px-2 py-2 text-sm font-medium transition-colors disabled:opacity-50',
                    kind === k ? 'border-brand-primary bg-brand-primary-soft text-brand-primary' : 'border-border-default text-text-muted hover:bg-bg-muted'
                  )}
                >
                  {KIND_LABELS[k]}
                </button>
              ))}
            </div>
          </div>
          <div className="space-y-1.5">
            <Label className="field-label">Color</Label>
            <ColorPicker value={color} onChange={setColor} />
          </div>
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={!name.trim() || saving}>
              {saving && <Loader2 className="size-4 animate-spin" />}Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
