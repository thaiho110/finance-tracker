import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Image, ScrollView, StyleSheet, Text, View } from 'react-native';
import { launchCamera, launchImageLibrary, type ImagePickerResponse } from 'react-native-image-picker';
import { formatVnd, type ParsedReceipt } from '@finance-tracker/shared';
import { useSession } from '../session/SessionContext';
import { Banner, Button, Chip, Field } from '../components/ui';
import {
  formFromReceipt,
  parseAmountInput,
  RECEIPT_WARNINGS,
  receiptProblems,
  receiptToTransaction,
  type ReceiptForm,
} from '../features/receipt';
import { colors, radius, space } from '../theme';

interface Photo {
  uri: string;
  name: string;
  type: string;
}

/** Downscaled on the phone: receipts read fine at ~2000px and upload much faster. */
const PICKER_OPTIONS = { mediaType: 'photo', maxWidth: 2400, maxHeight: 2400, quality: 0.9 } as const;

export function ScanReceiptScreen() {
  const { api } = useSession();
  const [photo, setPhoto] = useState<Photo | null>(null);
  const [receipt, setReceipt] = useState<ParsedReceipt | null>(null);
  const [form, setForm] = useState<ReceiptForm | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [reading, setReading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  useEffect(() => {
    api.categories.list().then(setCategories).catch(() => setCategories([]));
  }, [api]);

  const handlePick = async (response: ImagePickerResponse) => {
    if (response.didCancel) return;
    const asset = response.assets?.[0];
    if (response.errorCode || !asset?.uri) {
      setError(response.errorMessage ?? "Couldn't get the photo");
      return;
    }
    const picked = { uri: asset.uri, name: asset.fileName ?? 'receipt.jpg', type: asset.type ?? 'image/jpeg' };
    setPhoto(picked);
    setReceipt(null);
    setForm(null);
    setSaved(null);
    setError(null);
    setReading(true);
    try {
      const result = await api.imports.scanReceipt(picked);
      setReceipt(result);
      setForm(formFromReceipt(result));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't read this receipt");
    } finally {
      setReading(false);
    }
  };

  const save = async () => {
    if (!form || !receipt) return;
    setSaving(true);
    setError(null);
    try {
      await api.transactions.saveBatch([receiptToTransaction(form, receipt)]);
      setSaved(`${form.merchant.trim()} · ${formatVnd(parseAmountInput(form.amount))} saved`);
      setPhoto(null);
      setReceipt(null);
      setForm(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save');
    } finally {
      setSaving(false);
    }
  };

  const problems = form ? receiptProblems(form) : [];
  const update = (changes: Partial<ReceiptForm>) => setForm((current) => (current ? { ...current, ...changes } : current));

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      {saved ? <Banner tone="info">{saved}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}

      <View style={styles.pickers}>
        <Button title="Take photo" onPress={() => launchCamera(PICKER_OPTIONS).then(handlePick)} style={styles.flex} />
        <Button title="From library" variant="secondary" onPress={() => launchImageLibrary(PICKER_OPTIONS).then(handlePick)} style={styles.flex} />
      </View>
      <Text style={styles.note}>The photo is read on your server and not stored.</Text>

      {photo ? <Image source={{ uri: photo.uri }} style={styles.preview} resizeMode="contain" /> : null}
      {reading ? (
        <View style={styles.reading}>
          <ActivityIndicator color={colors.primary} />
          <Text style={styles.note}>Reading the receipt…</Text>
        </View>
      ) : null}

      {receipt && form ? (
        <View style={styles.form}>
          {receipt.isDuplicate ? <Banner tone="warning">A transaction with this amount around this date is already saved.</Banner> : null}
          {receipt.warnings.map((code) => <Banner key={code} tone="warning">{RECEIPT_WARNINGS[code] ?? code}</Banner>)}
          <Field label="Shop" value={form.merchant} onChangeText={(merchant) => update({ merchant })} invalid={!form.merchant.trim()} />
          <Field label="Date" value={form.date} onChangeText={(date) => update({ date })} placeholder="2026-10-17" />
          <Field
            label="Total (₫)"
            value={form.amount}
            onChangeText={(amount) => update({ amount })}
            keyboardType="number-pad"
            hint={parseAmountInput(form.amount) ? formatVnd(parseAmountInput(form.amount)) : undefined}
          />
          <Text style={styles.label}>Category</Text>
          <View style={styles.chips}>
            {(categories.includes(form.category) ? categories : [form.category, ...categories]).map((c) => (
              <Chip key={c} label={c} selected={c === form.category} onPress={() => update({ category: c })} />
            ))}
          </View>
          {receipt.lineItems.length > 0 && (
            <View style={styles.card}>
              <Text style={styles.label}>Items ({receipt.lineItems.length})</Text>
              {receipt.lineItems.map((line, index) => (
                <View key={`${line.item}-${index}`} style={styles.itemRow}>
                  <Text style={styles.itemName} numberOfLines={1}>{line.item}</Text>
                  <Text style={styles.itemPrice}>{formatVnd(line.price)}</Text>
                </View>
              ))}
            </View>
          )}
          {problems.length > 0 ? <Text style={styles.problems}>{problems.join(' · ')}</Text> : null}
          <Button title="Save transaction" onPress={save} loading={saving} disabled={problems.length > 0} />
        </View>
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: space(4), gap: space(4) },
  flex: { flex: 1 },
  pickers: { flexDirection: 'row', gap: space(3) },
  note: { fontSize: 13, color: colors.textMuted, textAlign: 'center' },
  preview: { width: '100%', height: 280, borderRadius: radius.lg, backgroundColor: colors.muted },
  reading: { alignItems: 'center', gap: space(2) },
  form: { gap: space(4) },
  label: { fontSize: 12, fontWeight: '500', color: colors.textMuted, textTransform: 'uppercase', letterSpacing: 0.5 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space(2) },
  card: { backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border, padding: space(4), gap: space(2) },
  itemRow: { flexDirection: 'row', justifyContent: 'space-between', gap: space(3) },
  itemName: { flex: 1, fontSize: 14, color: colors.text },
  itemPrice: { fontSize: 14, color: colors.text, fontVariant: ['tabular-nums'] },
  problems: { fontSize: 13, color: colors.warning, textAlign: 'center' },
});
