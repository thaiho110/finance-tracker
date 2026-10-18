import React, { useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { errorCodes, isErrorWithCode, pick, types } from '@react-native-documents/picker';
import { formatVnd, type Category, type ParsedTransaction } from '@finance-tracker/shared';
import { useSession } from '../session/SessionContext';
import { Banner, Button } from '../components/ui';
import { CategoryPicker } from '../components/CategoryPicker';
import { offerAlwaysUse } from '../features/alwaysUse';
import { defaultSelection, isComplete, rowsToTransactions } from '../features/csv';
import { colors, radius, space } from '../theme';

export function ImportCsvScreen() {
  const { api, can } = useSession();
  const [categories, setCategories] = useState<Category[]>([]);
  /** Row whose category is being changed. */
  const [editing, setEditing] = useState<ParsedTransaction | null>(null);

  useEffect(() => {
    api.categories.list().then(setCategories).catch(() => setCategories([]));
  }, [api]);

  const setCategory = (row: ParsedTransaction, category: Category) => {
    setRows((current) => current.map((r) => (r.rowIndex === row.rowIndex ? { ...r, categoryId: category.id, category: category.name } : r)));
    setEditing(null);
    if (can('CATEGORY_WRITE')) offerAlwaysUse(api, row.cleanMerchant || row.rawDescription || '', category, setSaved);
  };
  const [fileName, setFileName] = useState<string | null>(null);
  const [rows, setRows] = useState<ParsedTransaction[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState<'reading' | 'saving' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const choose = async () => {
    setError(null);
    setSaved(null);
    try {
      const [file] = await pick({ type: [types.csv, types.plainText] });
      setFileName(file.name ?? 'statement.csv');
      setBusy('reading');
      const parsed = await api.imports.parseCsv({ uri: file.uri, name: file.name ?? 'statement.csv', type: file.type ?? 'text/csv' });
      setRows(parsed);
      setSelected(defaultSelection(parsed));
    } catch (err) {
      if (isErrorWithCode(err) && err.code === errorCodes.OPERATION_CANCELED) return;
      setError(err instanceof Error ? err.message : "Couldn't read this file");
    } finally {
      setBusy(null);
    }
  };

  const toggle = (row: ParsedTransaction) => {
    if (!isComplete(row)) return;
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(row.rowIndex)) next.delete(row.rowIndex); else next.add(row.rowIndex);
      return next;
    });
  };

  const save = async () => {
    const requests = rowsToTransactions(rows, selected);
    setBusy('saving');
    setError(null);
    try {
      await api.transactions.saveBatch(requests);
      setSaved(`${requests.length} transaction${requests.length === 1 ? '' : 's'} saved`);
      setRows([]);
      setSelected(new Set());
      setFileName(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save');
    } finally {
      setBusy(null);
    }
  };

  const duplicates = rows.filter((row) => row.isDuplicate).length;
  const incomplete = rows.filter((row) => !isComplete(row)).length;

  return (
    <View style={styles.screen}>
      <FlatList
        contentContainerStyle={styles.content}
        data={rows}
        keyExtractor={(row) => String(row.rowIndex)}
        ListHeaderComponent={
          <View style={styles.header}>
            {saved ? <Banner tone="info">{saved}</Banner> : null}
            {error ? <Banner tone="danger">{error}</Banner> : null}
            <Button title={fileName ? 'Choose another file' : 'Choose a CSV statement'} onPress={choose} variant={fileName ? 'secondary' : 'primary'} />
            {busy === 'reading' ? <ActivityIndicator color={colors.primary} /> : null}
            {fileName && rows.length > 0 ? (
              <Text style={styles.summary}>
                {fileName}: {rows.length} rows
                {duplicates ? ` · ${duplicates} possible duplicate${duplicates === 1 ? '' : 's'}` : ''}
                {incomplete ? ` · ${incomplete} need fixing on the web app` : ''}
              </Text>
            ) : null}
          </View>
        }
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        renderItem={({ item }) => {
          const complete = isComplete(item);
          const on = selected.has(item.rowIndex);
          return (
            <Pressable
              onPress={() => toggle(item)}
              style={[styles.row, !on && styles.rowOff, item.isDuplicate && styles.rowDuplicate]}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: on, disabled: !complete }}
            >
              <View style={[styles.check, on && styles.checkOn]}>{on ? <Text style={styles.checkMark}>✓</Text> : null}</View>
              <View style={styles.rowMain}>
                <Text style={styles.merchant} numberOfLines={1}>{item.cleanMerchant || item.rawDescription || '—'}</Text>
                <Text style={styles.muted}>
                  {item.date ?? 'No date'} ·{' '}
                  <Text style={styles.link} onPress={() => setEditing(item)} accessibilityRole="button">{item.category} ▾</Text>
                  {item.isDuplicate ? ' · possible duplicate' : ''}
                </Text>
              </View>
              <Text style={styles.amount}>{formatVnd(item.amount)}</Text>
            </Pressable>
          );
        }}
      />
      <Modal visible={!!editing} transparent animationType="slide" onRequestClose={() => setEditing(null)}>
        <View style={styles.backdrop}>
          <View style={styles.sheet}>
            <Text style={styles.sheetTitle} numberOfLines={1}>Category for {editing?.cleanMerchant || editing?.rawDescription}</Text>
            {editing ? (
              <CategoryPicker
                value={editing.categoryId}
                categories={categories}
                current={{ id: editing.categoryId, name: editing.category }}
                onChange={(category) => setCategory(editing, category)}
              />
            ) : null}
            <Button title="Cancel" variant="secondary" onPress={() => setEditing(null)} />
          </View>
        </View>
      </Modal>
      {rows.length > 0 ? (
        <View style={styles.footer}>
          <Button title={`Save ${selected.size} transaction${selected.size === 1 ? '' : 's'}`} onPress={save} loading={busy === 'saving'} disabled={selected.size === 0} />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: space(4), paddingBottom: space(24) },
  header: { gap: space(3), marginBottom: space(3) },
  summary: { fontSize: 13, color: colors.textMuted },
  row: {
    flexDirection: 'row', alignItems: 'center', gap: space(3), padding: space(3),
    backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border,
  },
  rowOff: { opacity: 0.55 },
  rowDuplicate: { backgroundColor: colors.warningSoft },
  rowMain: { flex: 1, gap: space(1) },
  check: { width: 22, height: 22, borderRadius: 6, borderWidth: 1.5, borderColor: colors.border, alignItems: 'center', justifyContent: 'center' },
  checkOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  checkMark: { color: colors.onPrimary, fontSize: 13, fontWeight: '700' },
  merchant: { fontSize: 15, fontWeight: '600', color: colors.text },
  amount: { fontSize: 15, fontWeight: '600', color: colors.text, fontVariant: ['tabular-nums'] },
  muted: { fontSize: 12, color: colors.textMuted },
  link: { color: colors.primary, fontWeight: '600' },
  backdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.35)' },
  sheet: { backgroundColor: colors.canvas, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, padding: space(5), gap: space(4) },
  sheetTitle: { fontSize: 17, fontWeight: '600', color: colors.text },
  separator: { height: space(2) },
  footer: { position: 'absolute', left: 0, right: 0, bottom: 0, padding: space(4), backgroundColor: colors.canvas, borderTopWidth: 1, borderTopColor: colors.border },
});
