import React, { useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import type { Category, CategoryKind } from '@finance-tracker/shared';
import { useSession } from '../session/SessionContext';
import { Banner, Button, Field } from './ui';
import { colors, radius, space } from '../theme';

const KINDS: { kind: CategoryKind; label: string }[] = [
  { kind: 'EXPENSE', label: 'Spending' },
  { kind: 'INCOME', label: 'Income' },
  { kind: 'TRANSFER', label: 'Transfer' },
];
const COLORS = ['#2E933C', '#2A9D8F', '#2F80ED', '#6F42C1', '#D63384', '#DC3545', '#F29E4C', '#8B5E3C'];

/** A category shown for the current value even if it can't be picked any more (archived or hidden). */
export interface CurrentCategory {
  id: string;
  name: string;
  color?: string | null;
}

/**
 * Category chips with colored dots, grouped by kind, plus "+ New" (CATEGORY_WRITE) which creates one of the
 * user's own categories and selects it (ADR-015).
 */
export function CategoryPicker({ value, categories, onChange, current, disabled }: {
  value: string;
  categories: Category[];
  onChange: (category: Category) => void;
  current?: CurrentCategory;
  disabled?: boolean;
}) {
  const { can } = useSession();
  const [creating, setCreating] = useState(false);
  const extra = current && current.id === value && !categories.some((c) => c.id === value) ? current : null;

  return (
    <View style={styles.wrap}>
      {extra ? <CategoryChip name={extra.name} color={extra.color} selected onPress={() => undefined} /> : null}
      {KINDS.map(({ kind }) => categories.filter((c) => c.kind === kind).map((c) => (
        <CategoryChip key={c.id} name={c.name} color={c.color} selected={c.id === value} onPress={() => !disabled && onChange(c)} />
      )))}
      {!disabled && can('CATEGORY_WRITE') ? (
        <Pressable onPress={() => setCreating(true)} style={[styles.chip, styles.newChip]} accessibilityRole="button">
          <Text style={styles.newText}>+ New</Text>
        </Pressable>
      ) : null}
      <NewCategoryModal
        visible={creating}
        onClose={() => setCreating(false)}
        onCreated={(category) => { setCreating(false); onChange(category); }}
      />
    </View>
  );
}

export function CategoryChip({ name, color, selected, onPress }: {
  name: string;
  color?: string | null;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={[styles.chip, selected && styles.chipSelected]} accessibilityState={{ selected }}>
      <View style={[styles.dot, { backgroundColor: color || colors.textMuted }]} />
      <Text style={[styles.chipText, selected && styles.chipTextSelected]}>{name}</Text>
    </Pressable>
  );
}

function NewCategoryModal({ visible, onClose, onCreated }: {
  visible: boolean;
  onClose: () => void;
  onCreated: (category: Category) => void;
}) {
  const { api } = useSession();
  const [name, setName] = useState('');
  const [kind, setKind] = useState<CategoryKind>('EXPENSE');
  const [color, setColor] = useState(COLORS[2]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = async () => {
    setSaving(true);
    setError(null);
    try {
      const created = await api.categories.create({ name: name.trim(), kind, color });
      setName('');
      onCreated(created);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the category');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View style={styles.sheet}>
          <Text style={styles.title}>New category</Text>
          {error ? <Banner tone="danger">{error}</Banner> : null}
          <Field label="Name" value={name} onChangeText={setName} placeholder="e.g. Pets" autoFocus maxLength={100} />
          <View style={styles.row}>
            {KINDS.map((k) => (
              <Pressable key={k.kind} onPress={() => setKind(k.kind)} style={[styles.kind, kind === k.kind && styles.kindSelected]}>
                <Text style={[styles.chipText, kind === k.kind && styles.chipTextSelected]}>{k.label}</Text>
              </Pressable>
            ))}
          </View>
          <View style={styles.row}>
            {COLORS.map((c) => (
              <Pressable
                key={c}
                accessibilityLabel={`Color ${c}`}
                onPress={() => setColor(c)}
                style={[styles.swatch, { backgroundColor: c }, color === c && styles.swatchSelected]}
              />
            ))}
          </View>
          <Button title="Create" onPress={create} loading={saving} disabled={!name.trim()} />
          <Button title="Cancel" variant="secondary" onPress={onClose} />
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: space(2) },
  chip: {
    flexDirection: 'row', alignItems: 'center', gap: space(1.5), borderRadius: 999, borderWidth: 1,
    borderColor: colors.border, backgroundColor: colors.surface, paddingHorizontal: space(3), paddingVertical: space(1.5),
  },
  chipSelected: { backgroundColor: colors.primarySoft, borderColor: colors.primary },
  chipText: { fontSize: 13, color: colors.text },
  chipTextSelected: { color: colors.primary, fontWeight: '600' },
  dot: { width: 8, height: 8, borderRadius: 4 },
  newChip: { borderStyle: 'dashed', borderColor: colors.primary },
  newText: { fontSize: 13, color: colors.primary, fontWeight: '600' },
  backdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.35)' },
  sheet: { backgroundColor: colors.canvas, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, padding: space(5), gap: space(3) },
  title: { fontSize: 18, fontWeight: '600', color: colors.text },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: space(2) },
  kind: { flex: 1, alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: colors.border, paddingVertical: space(2.5) },
  kindSelected: { backgroundColor: colors.primarySoft, borderColor: colors.primary },
  swatch: { width: 28, height: 28, borderRadius: 14 },
  swatchSelected: { borderWidth: 3, borderColor: colors.text },
});
