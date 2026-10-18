import React, { useCallback, useLayoutEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import { useFocusEffect } from '@react-navigation/native';
import { ApiError, formatVnd, type BudgetStatus, type Overview, type OverviewCategory } from '@finance-tracker/shared';
import { useSession } from '../session/SessionContext';
import { Banner, Button, Centered, Field } from '../components/ui';
import { colors, radius, space } from '../theme';
import type { TabParams } from '../navigation/types';

type Props = BottomTabScreenProps<TabParams, 'Overview'>;

const statusColor: Record<BudgetStatus, string> = { OK: colors.primary, NEAR: colors.warning, OVER: colors.danger };

function monthString(d: { year: number; month: number }): string {
  return `${d.year}-${String(d.month + 1).padStart(2, '0')}`;
}

function monthLabel(d: { year: number; month: number }): string {
  return new Date(d.year, d.month).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
}

function deltaLabel(current: number, previous: number): string | null {
  if (previous === 0) return current === 0 ? null : 'new vs last month';
  const pct = ((current - previous) / Math.abs(previous)) * 100;
  return `${pct >= 0 ? '+' : ''}${pct.toFixed(0)}% vs last month`;
}

export function OverviewScreen({ navigation }: Props) {
  const { api, can } = useSession();
  const now = new Date();
  const [view, setView] = useState({ year: now.getFullYear(), month: now.getMonth() });
  const [data, setData] = useState<Overview | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<OverviewCategory | 'TOTAL' | null>(null);

  const canEdit = can('BUDGETS');
  const isCurrentMonth = view.year === now.getFullYear() && view.month === now.getMonth();

  const load = useCallback(async (month: string) => {
    try {
      setData(await api.overview.get(month));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the overview');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [api]);

  useFocusEffect(useCallback(() => {
    void load(monthString(view));
  }, [load, view]));

  // Badge the tab when something is near or over budget this month.
  const hasWarning = !!data?.budgetsEnabled
    && (data.totalBudget?.status === 'OVER' || data.totalBudget?.status === 'NEAR'
      || data.categories.some((c) => c.status === 'NEAR' || c.status === 'OVER'));
  useLayoutEffect(() => {
    navigation.setOptions({ tabBarBadge: hasWarning ? '!' : undefined });
  }, [navigation, hasWarning]);

  const prevMonth = () => setView((m) => (m.month === 0 ? { year: m.year - 1, month: 11 } : { ...m, month: m.month - 1 }));
  const nextMonth = () => { if (!isCurrentMonth) setView((m) => (m.month === 11 ? { year: m.year + 1, month: 0 } : { ...m, month: m.month + 1 })); };

  if (loading) {
    return <Centered><ActivityIndicator color={colors.primary} /></Centered>;
  }

  const ov = data;
  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); void load(monthString(view)); }} />}
    >
      {/* Month switcher */}
      <View style={styles.monthRow}>
        <Pressable onPress={prevMonth} hitSlop={10} accessibilityLabel="Previous month"><Text style={styles.chevron}>‹</Text></Pressable>
        <Text style={styles.monthLabel}>{monthLabel(view)}</Text>
        <Pressable onPress={nextMonth} hitSlop={10} disabled={isCurrentMonth} accessibilityLabel="Next month">
          <Text style={[styles.chevron, isCurrentMonth && styles.chevronDisabled]}>›</Text>
        </Pressable>
      </View>

      {error ? <Banner tone="danger">{error}</Banner> : null}

      {ov && (ov.income !== 0 || ov.expenses !== 0 || ov.categories.length > 0) ? (
        <>
          {/* Warning banner */}
          {hasWarning && (
            <Banner tone={ov.totalBudget?.status === 'OVER' || ov.categories.some((c) => c.status === 'OVER') ? 'danger' : 'warning'}>
              {bannerText(ov)}
            </Banner>
          )}

          {/* Stats */}
          <View style={styles.statRow}>
            <Stat label="Income" value={ov.income} delta={deltaLabel(ov.income, ov.previousIncome)} />
            <Stat label="Expenses" value={ov.expenses} delta={deltaLabel(ov.expenses, ov.previousExpenses)} />
          </View>
          <View style={styles.statRow}>
            <Stat label="Net" value={ov.net} delta={deltaLabel(ov.net, ov.previousIncome - ov.previousExpenses)} />
            {ov.budgetsEnabled && ov.totalBudget ? (
              <Pressable style={styles.statCard} onPress={() => canEdit && setEditing('TOTAL')}>
                <Text style={styles.statLabel}>Total budget</Text>
                <Text style={[styles.statValue, { color: statusColor[ov.totalBudget.status] }]}>
                  {formatVnd(ov.totalBudget.spent)}
                </Text>
                <Text style={styles.statDelta}>of {formatVnd(ov.totalBudget.amount)}</Text>
              </Pressable>
            ) : (
              <View style={styles.statCard}>
                <Text style={styles.statLabel}>Top merchant</Text>
                <Text style={styles.statValue} numberOfLines={1}>{ov.topMerchants[0]?.merchant ?? '—'}</Text>
                {ov.topMerchants[0] && <Text style={styles.statDelta}>{formatVnd(ov.topMerchants[0].total)}</Text>}
              </View>
            )}
          </View>

          {/* Category bars */}
          {ov.categories.length > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Spending by category</Text>
              {ov.categories.map((c) => (
                <CategoryBar key={c.categoryId} category={c} canEdit={canEdit} onEdit={() => setEditing(c)} />
              ))}
            </View>
          )}

          {/* Top merchants */}
          {ov.topMerchants.length > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Top merchants</Text>
              {ov.topMerchants.map((m) => (
                <View key={m.merchant} style={styles.merchantRow}>
                  <Text style={styles.merchantName} numberOfLines={1}>{m.merchant}</Text>
                  <Text style={styles.merchantAmount}>{formatVnd(m.total)}</Text>
                </View>
              ))}
            </View>
          )}
        </>
      ) : (
        <Centered>
          <Text style={styles.emptyTitle}>Nothing in {monthLabel(view)}</Text>
          <Text style={styles.muted}>No income or spending recorded for this month yet.</Text>
        </Centered>
      )}

      {editing && canEdit && (
        <BudgetEditor
          month={monthString(view)}
          target={editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); void load(monthString(view)); }}
        />
      )}
    </ScrollView>
  );
}

function bannerText(ov: Overview): string {
  const over = ov.categories.filter((c) => c.status === 'OVER').length;
  const near = ov.categories.filter((c) => c.status === 'NEAR').length;
  const parts: string[] = [];
  if (ov.totalBudget?.status === 'OVER') parts.push('your total budget is over');
  else if (ov.totalBudget?.status === 'NEAR') parts.push('your total budget is close');
  if (over > 0) parts.push(`${over} ${over === 1 ? 'category is' : 'categories are'} over budget`);
  if (near > 0) parts.push(`${near} ${near === 1 ? 'category is' : 'categories are'} close`);
  return `This month ${parts.join(', ')}.`;
}

function Stat({ label, value, delta }: { label: string; value: number; delta: string | null }) {
  return (
    <View style={styles.statCard}>
      <Text style={styles.statLabel}>{label}</Text>
      <Text style={styles.statValue}>{formatVnd(value)}</Text>
      {delta && <Text style={styles.statDelta}>{delta}</Text>}
    </View>
  );
}

function CategoryBar({ category, canEdit, onEdit }: { category: OverviewCategory; canEdit: boolean; onEdit: () => void }) {
  const pct = category.budget && category.budget > 0 ? Math.min(100, Math.max(0, (category.spent / category.budget) * 100)) : null;
  const barColor = category.status ? statusColor[category.status] : colors.primary;
  return (
    <Pressable style={styles.catRow} onPress={onEdit} disabled={!canEdit}>
      <View style={styles.catHeader}>
        <View style={styles.catName}>
          <View style={[styles.dot, { backgroundColor: category.color || colors.textMuted }]} />
          <Text style={styles.catLabel} numberOfLines={1}>{category.name}</Text>
        </View>
        <Text style={[styles.catAmount, category.status && { color: statusColor[category.status] }]}>
          {formatVnd(category.spent)}{category.budget != null && category.budget > 0 ? ` / ${formatVnd(category.budget)}` : ''}
        </Text>
      </View>
      <View style={styles.track}>
        <View style={[styles.fill, { width: `${pct ?? 100}%`, backgroundColor: pct != null ? barColor : colors.muted }]} />
      </View>
    </Pressable>
  );
}

function BudgetEditor({ month, target, onClose, onSaved }: {
  month: string;
  target: OverviewCategory | 'TOTAL';
  onClose: () => void;
  onSaved: () => void;
}) {
  const { api } = useSession();
  const isTotal = target === 'TOTAL';
  const current = isTotal ? 0 : (target.budget ?? 0);
  const name = isTotal ? 'Total budget' : target.name;
  const [value, setValue] = useState(current === 0 ? '' : String(current));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save(amount: number) {
    if (Number.isNaN(amount) || amount < 0) { setError('Enter a budget of 0 or more'); return; }
    setSaving(true);
    try {
      await api.budgets.set({ categoryId: isTotal ? undefined : target.categoryId, amount, month });
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the budget');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal transparent animationType="fade" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable style={styles.sheet} onPress={() => {}}>
          <Text style={styles.sheetTitle}>{name}</Text>
          <Text style={styles.muted}>Monthly budget in VND, from {month} on. Set 0 to remove it.</Text>
          <Field
            label="Amount (VND)"
            value={value}
            onChangeText={setValue}
            keyboardType="numeric"
            placeholder="No budget"
            invalid={!!error}
            hint={error ?? undefined}
          />
          <View style={styles.sheetActions}>
            <Button title="Cancel" variant="secondary" onPress={onClose} style={styles.flex} />
            <Button
              title="Save"
              loading={saving}
              onPress={() => save(value.trim() === '' ? 0 : Number(value.replace(/[^\d.-]/g, '')))}
              style={styles.flex}
            />
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: space(4), gap: space(3) },
  monthRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space(4) },
  monthLabel: { fontSize: 18, fontWeight: '600', color: colors.text },
  chevron: { fontSize: 28, color: colors.primary, paddingHorizontal: space(2) },
  chevronDisabled: { color: colors.border },
  statRow: { flexDirection: 'row', gap: space(3) },
  statCard: { flex: 1, gap: space(1), padding: space(4), backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border },
  statLabel: { fontSize: 12, fontWeight: '500', color: colors.textMuted, textTransform: 'uppercase', letterSpacing: 0.5 },
  statValue: { fontSize: 20, fontWeight: '700', color: colors.text, fontVariant: ['tabular-nums'] },
  statDelta: { fontSize: 12, color: colors.textMuted },
  section: { gap: space(3), padding: space(4), backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border },
  sectionTitle: { fontSize: 16, fontWeight: '600', color: colors.text },
  catRow: { gap: space(2) },
  catHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space(2) },
  catName: { flexDirection: 'row', alignItems: 'center', gap: space(2), flex: 1 },
  catLabel: { fontSize: 14, color: colors.text, flexShrink: 1 },
  catAmount: { fontSize: 14, color: colors.text, fontVariant: ['tabular-nums'] },
  dot: { width: 10, height: 10, borderRadius: 5 },
  track: { height: 6, borderRadius: 3, backgroundColor: colors.muted, overflow: 'hidden' },
  fill: { height: 6, borderRadius: 3 },
  merchantRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space(3) },
  merchantName: { fontSize: 14, color: colors.text, flex: 1 },
  merchantAmount: { fontSize: 14, color: colors.text, fontWeight: '600', fontVariant: ['tabular-nums'] },
  emptyTitle: { fontSize: 17, fontWeight: '600', color: colors.text },
  muted: { fontSize: 13, color: colors.textMuted, textAlign: 'center' },
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'flex-end' },
  sheet: { backgroundColor: colors.surface, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, padding: space(5), gap: space(3) },
  sheetTitle: { fontSize: 18, fontWeight: '700', color: colors.text },
  sheetActions: { flexDirection: 'row', gap: space(3) },
  flex: { flex: 1 },
});
