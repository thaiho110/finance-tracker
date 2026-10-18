import { useMemo, useState } from 'react';
import { Archive, ArchiveRestore, Eye, EyeOff, Pencil, Plus, Search, Trash2, Wand2 } from 'lucide-react';
import { toast } from 'sonner';
import type { Category } from '@finance-tracker/shared';
import {
  useDeleteCategoryMutation,
  useDeleteRuleMutation,
  useGetAllCategoriesQuery,
  useGetCategoriesQuery,
  useGetRulesQuery,
  useSaveRuleMutation,
  useSetCategoryHiddenMutation,
  useUpdateCategoryMutation,
} from '@/store/api/category-api';
import { useCan } from '@/lib/permissions';
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
import { NewCategoryDialog } from '@/components/category/new-category-dialog';
import { cn } from 'cn';

type Tab = 'categories' | 'rules';

/**
 * Settings → Categories (ADR-015): the user's own categories, which defaults they see, and the keyword
 * rules that categorize imports. Reading needs CATEGORY_READ; every change needs CATEGORY_WRITE.
 */
export default function CategoriesSettingsPage() {
  const [tab, setTab] = useState<Tab>('categories');
  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <div>
        <h1 className="page-title">Categories</h1>
        <p className="text-sm text-text-muted">Organize your spending and teach Kept which merchant goes where.</p>
      </div>
      <div className="inline-flex w-fit rounded-[10px] bg-bg-muted p-1">
        {([['categories', 'Categories'], ['rules', 'Rules']] as const).map(([key, label]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={cn(
              'rounded-[8px] px-4 py-1.5 text-sm font-medium transition-colors',
              tab === key ? 'bg-bg-surface text-text-default shadow-sm' : 'text-text-muted hover:text-text-default'
            )}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === 'categories' ? <CategoriesTab /> : <RulesTab />}
    </div>
  );
}

/* ───────────────────────── categories ───────────────────────── */

function CategoriesTab() {
  const can = useCan();
  const writable = can('CATEGORY_WRITE');
  const { data: all = [], isLoading } = useGetAllCategoriesQuery();
  const { data: pickable = [] } = useGetCategoriesQuery();
  const [updateCategory, { isLoading: updating }] = useUpdateCategoryMutation();
  const [setHidden] = useSetCategoryHiddenMutation();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Category | null>(null);
  const [removing, setRemoving] = useState<Category | null>(null);

  const own = all.filter((c) => !c.system);
  const defaults = all.filter((c) => c.system);

  const change = async (category: Category, changes: Parameters<typeof updateCategory>[0]['changes'], done: string) => {
    try {
      await updateCategory({ id: category.id, changes }).unwrap();
      toast.success(done);
      return true;
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not update the category'));
      return false;
    }
  };

  const toggleHidden = async (category: Category) => {
    try {
      await setHidden({ id: category.id, hidden: !category.hidden }).unwrap();
      toast.success(category.hidden ? `${category.name} is shown again` : `${category.name} is hidden from pickers`);
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not change it'));
    }
  };

  if (isLoading) return <Skeleton className="h-64 w-full" />;

  return (
    <>
      <section className="rounded-[12px] border border-border-default bg-bg-surface">
        <div className="flex items-center justify-between border-b border-border-default px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-text-default">Your categories</h2>
            <p className="text-sm text-text-muted">Only you see these.</p>
          </div>
          {writable && <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />New category</Button>}
        </div>
        {own.length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-text-muted">
            No categories of your own yet. Add one here, or pick "New category" when you review an import.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow><TableHead>Name</TableHead><TableHead className="w-32">Counts as</TableHead><TableHead className="w-36" /></TableRow>
            </TableHeader>
            <TableBody>
              {own.map((c) => (
                <TableRow key={c.id} className={cn(c.archived && 'opacity-60')}>
                  <TableCell>
                    <CategoryBadge category={c.name} color={c.color} />
                    {c.archived && <Badge variant="outline" className="ml-2">Archived</Badge>}
                  </TableCell>
                  <TableCell className="text-sm text-text-muted">{KIND_LABELS[c.kind]}</TableCell>
                  <TableCell className="text-right">
                    {writable && (
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon-sm" title="Edit" onClick={() => setEditing(c)}><Pencil className="size-3.5" /></Button>
                        {c.archived ? (
                          <Button variant="ghost" size="icon-sm" title="Restore" onClick={() => change(c, { archived: false }, `${c.name} restored`)}>
                            <ArchiveRestore className="size-3.5" />
                          </Button>
                        ) : (
                          <Button variant="ghost" size="icon-sm" title="Remove" onClick={() => setRemoving(c)}><Trash2 className="size-3.5" /></Button>
                        )}
                      </div>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>

      <section className="rounded-[12px] border border-border-default bg-bg-surface">
        <div className="border-b border-border-default px-5 py-4">
          <h2 className="text-base font-semibold text-text-default">Defaults</h2>
          <p className="text-sm text-text-muted">Shared by everyone and kept up to date by the administrator. Hide the ones you don't use.</p>
        </div>
        <div className="grid gap-2 p-4 sm:grid-cols-2">
          {defaults.filter((c) => !c.archived).map((c) => (
            <div key={c.id} className={cn('flex items-center justify-between rounded-[8px] border border-border-default px-3 py-2', c.hidden && 'opacity-60')}>
              <div className="flex items-center gap-2">
                <CategoryBadge category={c.name} color={c.color} />
                <span className="text-xs text-text-muted">{KIND_LABELS[c.kind]}</span>
              </div>
              {writable && c.name !== 'Other' && (
                <Button variant="ghost" size="icon-sm" title={c.hidden ? 'Show' : 'Hide'} onClick={() => toggleHidden(c)}>
                  {c.hidden ? <Eye className="size-3.5" /> : <EyeOff className="size-3.5" />}
                </Button>
              )}
            </div>
          ))}
        </div>
      </section>

      <NewCategoryDialog open={creating} onOpenChange={setCreating} />
      <EditCategoryDialog
        category={editing}
        saving={updating}
        onClose={() => setEditing(null)}
        onSave={async (changes) => {
          if (!editing) return;
          if (Object.keys(changes).length === 0 || await change(editing, changes, 'Category updated')) setEditing(null);
        }}
      />
      <RemoveCategoryDialog category={removing} pickable={pickable} onClose={() => setRemoving(null)} />
    </>
  );
}

/** Delete an unused category, or archive a used one; optionally move its transactions first. */
function RemoveCategoryDialog({ category, pickable, onClose }: { category: Category | null; pickable: Category[]; onClose: () => void }) {
  const [moveTo, setMoveTo] = useState('');
  const [remove, { isLoading }] = useDeleteCategoryMutation();
  const targets = pickable.filter((c) => c.id !== category?.id);

  const submit = async () => {
    if (!category) return;
    try {
      const result = await remove({ id: category.id, moveTo: moveTo || undefined }).unwrap();
      const moved = result.moved > 0 ? `${result.moved} transaction${result.moved !== 1 ? 's' : ''} moved. ` : '';
      toast.success(result.outcome === 'DELETED'
        ? `${moved}${category.name} deleted`
        : `${moved}${category.name} archived — it stays on older transactions`);
      setMoveTo('');
      onClose();
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not remove the category'));
    }
  };

  return (
    <Dialog open={!!category} onOpenChange={(open) => { if (!open) { setMoveTo(''); onClose(); } }}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle>Remove {category?.name}?</DialogTitle>
          <DialogDescription>
            If no transaction uses it, it's deleted. Otherwise it's archived: hidden from pickers and rules, but older
            transactions keep it.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1.5">
          <Label className="field-label">Move its transactions to (optional)</Label>
          <CategorySelect
            value={moveTo}
            categories={targets}
            placeholder="Keep them where they are"
            onChange={(c) => setMoveTo(c.id)}
          />
          {moveTo && (
            <button className="text-xs text-text-muted underline" onClick={() => setMoveTo('')}>Don't move them</button>
          )}
        </div>
        <DialogFooter>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="destructive" onClick={submit} disabled={isLoading}>Remove</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/* ───────────────────────── rules ───────────────────────── */

function RulesTab() {
  const can = useCan();
  const writable = can('CATEGORY_WRITE');
  const { data: rules = [], isLoading } = useGetRulesQuery('own');
  const { data: systemRules = [] } = useGetRulesQuery('system');
  const { data: categories = [] } = useGetCategoriesQuery();
  const [saveRule, { isLoading: saving }] = useSaveRuleMutation();
  const [deleteRule] = useDeleteRuleMutation();
  const [keyword, setKeyword] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [applyToPast, setApplyToPast] = useState(true);
  const [search, setSearch] = useState('');

  const colorOf = useMemo(() => new Map(categories.map((c) => [c.id, c.color])), [categories]);
  const filteredSystem = useMemo(() => {
    const q = search.trim().toUpperCase();
    return q ? systemRules.filter((r) => r.keyword.includes(q) || (r.categoryName ?? '').toUpperCase().includes(q)) : systemRules;
  }, [systemRules, search]);

  const add = async () => {
    try {
      const result = await saveRule({ keyword: keyword.trim(), categoryId, applyToPast }).unwrap();
      toast.success(result.moved > 0
        ? `Rule saved; ${result.moved} past transaction${result.moved !== 1 ? 's' : ''} moved from Other`
        : 'Rule saved');
      setKeyword('');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not save the rule'));
    }
  };

  const remove = async (id: string) => {
    try {
      await deleteRule(id).unwrap();
      toast.success('Rule deleted');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not delete the rule'));
    }
  };

  return (
    <>
      <section className="rounded-[12px] border border-border-default bg-bg-surface">
        <div className="border-b border-border-default px-5 py-4">
          <h2 className="text-base font-semibold text-text-default">Your rules</h2>
          <p className="text-sm text-text-muted">
            When a merchant contains the keyword (as a whole word, accents ignored), imports use that category. Your rules
            win over the defaults.
          </p>
        </div>
        {writable && (
          <form
            className="flex flex-wrap items-end gap-3 border-b border-border-default px-5 py-4"
            onSubmit={(e) => { e.preventDefault(); if (keyword.trim() && categoryId) add(); }}
          >
            <div className="flex min-w-[200px] flex-1 flex-col gap-1.5">
              <Label htmlFor="rule-keyword" className="field-label">Merchant keyword</Label>
              <Input id="rule-keyword" placeholder="e.g. Highlands Coffee" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
            </div>
            <div className="flex w-56 flex-col gap-1.5">
              <Label className="field-label">Category</Label>
              <CategorySelect value={categoryId} categories={categories} onChange={(c) => setCategoryId(c.id)} />
            </div>
            <label className="flex h-9 items-center gap-2 text-sm text-text-muted">
              <input type="checkbox" checked={applyToPast} onChange={(e) => setApplyToPast(e.target.checked)} />
              Also move past ones in Other
            </label>
            <Button type="submit" disabled={!keyword.trim() || !categoryId || saving}><Wand2 className="size-4" />Add rule</Button>
          </form>
        )}
        {isLoading ? (
          <Skeleton className="m-5 h-24" />
        ) : rules.length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-text-muted">
            No rules yet. When you change a category during review, Kept offers to remember it.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow><TableHead>Keyword</TableHead><TableHead>Category</TableHead><TableHead className="w-16" /></TableRow>
            </TableHeader>
            <TableBody>
              {rules.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="text-mono text-sm font-medium">{r.keyword}</TableCell>
                  <TableCell><CategoryBadge category={r.categoryName ?? '—'} color={colorOf.get(r.categoryId)} /></TableCell>
                  <TableCell className="text-right">
                    {writable && <Button variant="ghost" size="icon-sm" title="Delete" onClick={() => remove(r.id)}><Trash2 className="size-3.5" /></Button>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>

      <section className="rounded-[12px] border border-border-default bg-bg-surface">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border-default px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-text-default">Default rules</h2>
            <p className="text-sm text-text-muted">{systemRules.length} merchants Kept already knows. Add your own rule to override one.</p>
          </div>
          <div className="relative w-56">
            <Search className="absolute left-2.5 top-2.5 size-4 text-text-muted" />
            <Input className="pl-8" placeholder="Search" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
        </div>
        <div className="max-h-[360px] overflow-y-auto">
          <Table>
            <TableBody>
              {filteredSystem.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="text-mono text-sm">{r.keyword}</TableCell>
                  <TableCell><CategoryBadge category={r.categoryName ?? '—'} color={colorOf.get(r.categoryId)} /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </section>
      <p className="flex items-center gap-1.5 text-xs text-text-muted"><Archive className="size-3.5" />Rules pointing at an archived or hidden category are skipped.</p>
    </>
  );
}
