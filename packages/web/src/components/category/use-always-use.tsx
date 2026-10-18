import { useCallback } from 'react';
import { toast } from 'sonner';
import type { Category } from '@finance-tracker/shared';
import { usePreviewRuleMutation, useSaveRuleMutation } from '@/store/api/category-api';
import { useCan } from '@/lib/permissions';
import { apiErrorMessage } from '@/lib/api-error';

/**
 * After the user picks a category by hand: "Always use Coffee for HIGHLANDS COFFEE?" (ADR-015). Accepting
 * saves a rule; if past transactions still in Other match, a second prompt offers to move them too.
 * Does nothing without CATEGORY_WRITE, for an empty merchant, or for Other itself.
 */
export function useAlwaysUsePrompt(): (merchant: string, category: Category) => void {
  const can = useCan();
  const [preview] = usePreviewRuleMutation();
  const [saveRule] = useSaveRuleMutation();

  return useCallback((merchant: string, category: Category) => {
    const keyword = merchant.trim();
    if (!keyword || !can('CATEGORY_WRITE') || (category.system && category.name === 'Other')) return;

    const save = async (applyToPast: boolean) => {
      try {
        const result = await saveRule({ keyword, categoryId: category.id, applyToPast }).unwrap();
        toast.success(result.moved > 0
          ? `Rule saved and ${result.moved} past transaction${result.moved !== 1 ? 's' : ''} moved to ${category.name}`
          : `${result.rule.keyword} will go to ${category.name} from now on`);
      } catch (err) {
        toast.error(apiErrorMessage(err, 'Could not save the rule'));
      }
    };

    toast(`Always use ${category.name} for ${keyword.toUpperCase()}?`, {
      id: `always-use-${keyword.toUpperCase()}`,
      duration: 8000,
      action: {
        label: 'Always',
        onClick: async () => {
          let matching = 0;
          try {
            matching = (await preview(keyword).unwrap()).matching;
          } catch { /* the rule can still be saved */ }
          if (matching === 0) {
            await save(false);
            return;
          }
          toast(`Also move ${matching} past transaction${matching !== 1 ? 's' : ''} from Other to ${category.name}?`, {
            duration: 10000,
            action: { label: 'Move them', onClick: () => save(true) },
            cancel: { label: 'Only new ones', onClick: () => save(false) },
          });
        },
      },
    });
  }, [can, preview, saveRule]);
}
