import { useMemo, useState } from 'react';
import { ArchiveRestore, Archive, Pencil, Plus, Search, Trash2, Wand2 } from 'lucide-react';
import { toast } from 'sonner';
import type { Category, CategoryChanges, CategoryKind } from '@finance-tracker/shared';
import {
  useCreateSystemCategoryMutation,
  useDeleteSystemRuleMutation,
  useGetSystemCategoriesQuery,
  useGetSystemRulesQuery,
  useSaveSystemRuleMutation,
  useUpdateSystemCategoryMutation,
} from '@/store/api/category-api';
import { apiErrorMessage } from '@/lib/api-error';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { CategoryBadge } from '@/components/ui/category-badge';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { CategorySelect } from '@/components/category/category-select';
import { EditCategoryDialog, KIND_LABELS } from '@/components/category/edit-category-dialog';
import { ColorPicker } from '@/components/category/new-category-dialog';
import { CATEGORY_COLORS } from '@/components/category/category-colors';
import { cn } from 'cn';

/**
 * Default categories and keyword rules shared by every user (CATEGORY_ADMIN, ADR-015/016). Defaults are
 * archived instead of deleted because anyone's transactions may use them. Every change is in the audit log.
 */
export default function AdminCategoriesPage() {
  const { data: categories = [], isLoading } = useGetSystemCategoriesQuery();
  const { data: rules = [] } = useGetSystemRulesQuery();
  const [updateCategory, { isLoading: updating }] = useUpdateSystemCategoryMutation();
  const [saveRule, { isLoading: savingRule }] = useSaveSystemRuleMutation();
  const [deleteRule] = useDeleteSystemRuleMutation();
  const [editing, setEditing] = useState<Category | null>(null);
  const [creating, setCreating] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [ruleCategory, setRuleCategory] = useState('');
  const [search, setSearch] = useState('');

  const active = categories.filter((c) => !c.archived);
  const colorOf = useMemo(() => new Map(categories.map((c) => [c.id, c.color])), [categories]);
  const shownRules = useMemo(() => {
    const q = search.trim().toUpperCase();
    return q ? rules.filter((r) => r.keyword.includes(q) || (r.categoryName ?? '').toUpperCase().includes(q)) : rules;
  }, [rules, search]);

  const update = async (category: Category, changes: CategoryChanges, done: string) => {
    try {
      await updateCategory({ id: category.id, changes }).unwrap();
      toast.success(done);
      return true;
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not update the category'));
      return false;
    }
  };

  const addRule = async () => {
    try {
      const saved = await saveRule({ keyword: keyword.trim(), categoryId: ruleCategory }).unwrap();
      toast.success(`${saved.keyword} → ${saved.categoryName}`);
      setKeyword('');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not save the rule'));
    }
  };

  const removeRule = async (id: string, label: string) => {
    try {
      await deleteRule(id).unwrap();
      toast.success(`Rule ${label} deleted`);
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not delete the rule'));
    }
  };

  if (isLoading) return <Skeleton className="h-64 w-full" />;

  return (
    <div className="flex flex-col gap-6">
      <section className="rounded-[12px] border border-border-default bg-bg-surface">
        <div className="flex items-center justify-between border-b border-border-default px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-text-default">Default categories</h2>
            <p className="text-sm text-text-muted">Every user sees these unless they hide one. Archive instead of deleting.</p>
          </div>
          <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />Add default</Button>
        </div>
        <Table>
          <TableHeader>
            <TableRow><TableHead>Name</TableHead><TableHead className="w-32">Counts as</TableHead><TableHead className="w-28" /></TableRow>
          </TableHeader>
          <TableBody>
            {categories.map((c) => (
              <TableRow key={c.id} className={cn(c.archived && 'opacity-60')}>
                <TableCell>
                  <CategoryBadge category={c.name} color={c.color} />
                  {c.archived && <Badge variant="outline" className="ml-2">Archived</Badge>}
                </TableCell>
                <TableCell className="text-sm text-text-muted">{KIND_LABELS[c.kind]}</TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="icon-sm" title="Edit" onClick={() => setEditing(c)}><Pencil className="size-3.5" /></Button>
                    {c.name !== 'Other' && (c.archived ? (
                      <Button variant="ghost" size="icon-sm" title="Restore" onClick={() => update(c, { archived: false }, `${c.name} restored`)}>
                        <ArchiveRestore className="size-3.5" />
                      </Button>
                    ) : (
                      <Button variant="ghost" size="icon-sm" title="Archive" onClick={() => update(c, { archived: true }, `${c.name} archived`)}>
                        <Archive className="size-3.5" />
                      </Button>
                    ))}
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </section>

      <section className="rounded-[12px] border border-border-default bg-bg-surface">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border-default px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-text-default">Default rules</h2>
            <p className="text-sm text-text-muted">Merchant keyword → category, for everyone. A user's own rule wins over these.</p>
          </div>
          <div className="relative w-56">
            <Search className="absolute left-2.5 top-2.5 size-4 text-text-muted" />
            <Input className="pl-8" placeholder="Search" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
        </div>
        <form
          className="flex flex-wrap items-end gap-3 border-b border-border-default px-5 py-4"
          onSubmit={(e) => { e.preventDefault(); if (keyword.trim() && ruleCategory) addRule(); }}
        >
          <div className="flex min-w-[200px] flex-1 flex-col gap-1.5">
            <Label htmlFor="sys-rule-keyword" className="field-label">Merchant keyword</Label>
            <Input id="sys-rule-keyword" placeholder="e.g. Bách Hóa Xanh" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
          </div>
          <div className="flex w-56 flex-col gap-1.5">
            <Label className="field-label">Category</Label>
            <CategorySelect value={ruleCategory} categories={active} onChange={(c) => setRuleCategory(c.id)} />
          </div>
          <Button type="submit" disabled={!keyword.trim() || !ruleCategory || savingRule}><Wand2 className="size-4" />Save rule</Button>
        </form>
        <div className="max-h-[420px] overflow-y-auto">
          <Table>
            <TableBody>
              {shownRules.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="text-mono text-sm">{r.keyword}</TableCell>
                  <TableCell><CategoryBadge category={r.categoryName ?? '—'} color={colorOf.get(r.categoryId)} /></TableCell>
                  <TableCell className="w-16 text-right">
                    <Button variant="ghost" size="icon-sm" title="Delete" onClick={() => removeRule(r.id, r.keyword)}><Trash2 className="size-3.5" /></Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </section>

      <EditCategoryDialog
        category={editing}
        saving={updating}
        onClose={() => setEditing(null)}
        onSave={async (changes) => {
          if (!editing) return;
          if (Object.keys(changes).length === 0 || await update(editing, changes, 'Default updated')) setEditing(null);
        }}
      />
      <NewSystemCategoryDialog open={creating} onOpenChange={setCreating} />
    </div>
  );
}

function NewSystemCategoryDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const [name, setName] = useState('');
  const [kind, setKind] = useState<CategoryKind>('EXPENSE');
  const [color, setColor] = useState(CATEGORY_COLORS[4]);
  const [create, { isLoading }] = useCreateSystemCategoryMutation();

  const submit = async () => {
    try {
      const created = await create({ name: name.trim(), kind, color }).unwrap();
      toast.success(`Default "${created.name}" added`);
      setName('');
      onOpenChange(false);
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not add the category'));
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[420px]">
        <DialogHeader>
          <DialogTitle>Add a default category</DialogTitle>
          <DialogDescription>Every user will see it in their pickers.</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); if (name.trim()) submit(); }}>
          <div className="space-y-1.5">
            <Label htmlFor="sys-category-name" className="field-label">Name</Label>
            <Input id="sys-category-name" autoFocus maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label className="field-label">Counts as</Label>
            <div className="grid grid-cols-3 gap-2">
              {(Object.keys(KIND_LABELS) as CategoryKind[]).map((k) => (
                <button
                  key={k}
                  type="button"
                  onClick={() => setKind(k)}
                  className={cn(
                    'rounded-lg border px-2 py-2 text-sm font-medium',
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
            <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>Cancel</Button>
            <Button type="submit" disabled={!name.trim() || isLoading}>Add</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
