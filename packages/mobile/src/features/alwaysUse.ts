import { Alert } from 'react-native';
import type { ApiClient, Category } from '@finance-tracker/shared';

/**
 * After a manual category change: "Always use Coffee for HIGHLANDS COFFEE?" (ADR-015). Accepting saves a
 * rule; when past transactions still in Other match, a second question offers to move them too.
 * Callers check CATEGORY_WRITE first.
 */
export function offerAlwaysUse(api: ApiClient, merchant: string, category: Category, onDone?: (message: string) => void): void {
  const keyword = merchant.trim();
  if (!keyword || (category.system && category.name === 'Other')) return;

  const save = async (applyToPast: boolean) => {
    try {
      const result = await api.categories.saveRule({ keyword, categoryId: category.id, applyToPast });
      onDone?.(result.moved > 0
        ? `Rule saved; ${result.moved} past transaction${result.moved !== 1 ? 's' : ''} moved to ${category.name}`
        : `${result.rule.keyword} will go to ${category.name} from now on`);
    } catch (err) {
      onDone?.(err instanceof Error ? err.message : 'Could not save the rule');
    }
  };

  Alert.alert(`Always use ${category.name}?`, `For ${keyword.toUpperCase()} from now on.`, [
    { text: 'Not now', style: 'cancel' },
    {
      text: 'Always',
      onPress: async () => {
        let matching = 0;
        try {
          matching = (await api.categories.previewRule(keyword)).matching;
        } catch { /* the rule can still be saved */ }
        if (matching === 0) {
          await save(false);
          return;
        }
        Alert.alert(
          `Also move ${matching} past transaction${matching !== 1 ? 's' : ''}?`,
          `They're in Other now and match ${keyword.toUpperCase()}.`,
          [
            { text: 'Only new ones', onPress: () => save(false) },
            { text: 'Move them', onPress: () => save(true) },
          ],
        );
      },
    },
  ]);
}
