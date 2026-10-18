import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, Text, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { formatVnd, isValidAmount, isValidIsoDate, type Transaction } from '@finance-tracker/shared';
import { useSession } from '../session/SessionContext';
import { Banner, Button, Centered, Chip, Field } from '../components/ui';
import { parseAmountInput } from '../features/receipt';
import { colors, radius, space } from '../theme';
import type { TransactionsStackParams } from '../navigation/types';

type Props = NativeStackScreenProps<TransactionsStackParams, 'TransactionDetail'>;

export function TransactionDetailScreen({ route, navigation }: Props) {
  const { api, can } = useSession();
  const [tx, setTx] = useState<Transaction | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [merchant, setMerchant] = useState('');
  const [date, setDate] = useState('');
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const editable = can('TRANSACTION_WRITE');

  useEffect(() => {
    (async () => {
      try {
        const [loaded, list] = await Promise.all([api.transactions.get(route.params.id), api.categories.list()]);
        setTx(loaded);
        setCategories(list.includes(loaded.category) ? list : [loaded.category, ...list]);
        setMerchant(loaded.cleanMerchant);
        setDate(loaded.date);
        setAmount(String(Math.abs(loaded.amount)));
        setCategory(loaded.category);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not load the transaction');
      }
    })();
  }, [api, route.params.id]);

  const total = parseAmountInput(amount);
  const valid = !!merchant.trim() && isValidIsoDate(date) && isValidAmount(total);

  const save = async () => {
    if (!tx || !valid) return;
    setSaving(true);
    setError(null);
    try {
      await api.transactions.update(tx.id, {
        date,
        rawDescription: tx.rawDescription,
        cleanMerchant: merchant.trim(),
        amount: total as number,
        category,
      });
      navigation.goBack();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save');
    } finally {
      setSaving(false);
    }
  };

  const remove = () => {
    if (!tx) return;
    Alert.alert('Delete transaction?', `${tx.cleanMerchant} · ${formatVnd(tx.amount)}`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          try {
            await api.transactions.remove(tx.id);
            navigation.goBack();
          } catch (err) {
            setError(err instanceof Error ? err.message : 'Could not delete');
          }
        },
      },
    ]);
  };

  if (!tx) {
    return <Centered>{error ? <Banner tone="danger">{error}</Banner> : <ActivityIndicator color={colors.primary} />}</Centered>;
  }

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <Field label="Merchant" value={merchant} onChangeText={setMerchant} editable={editable} invalid={!merchant.trim()} />
      <Field label="Date" value={date} onChangeText={setDate} editable={editable} placeholder="2026-10-17" invalid={!isValidIsoDate(date)} />
      <Field
        label="Amount (₫)"
        value={amount}
        onChangeText={setAmount}
        editable={editable}
        keyboardType="number-pad"
        invalid={!isValidAmount(total)}
        hint={isValidAmount(total) ? formatVnd(total) : undefined}
      />
      <Text style={styles.label}>Category</Text>
      <View style={styles.chips}>
        {categories.map((c) => <Chip key={c} label={c} selected={c === category} onPress={() => editable && setCategory(c)} />)}
      </View>

      {tx.receiptItems.length > 0 && (
        <View style={styles.card}>
          <Text style={styles.label}>Receipt items</Text>
          {tx.receiptItems.map((item) => (
            <View key={item.id} style={styles.itemRow}>
              <Text style={styles.itemName} numberOfLines={1}>{item.itemDescription}</Text>
              <Text style={styles.itemPrice}>{formatVnd(item.price)}</Text>
            </View>
          ))}
        </View>
      )}

      {editable && (
        <View style={styles.actions}>
          <Button title="Save changes" onPress={save} loading={saving} disabled={!valid} />
          <Button title="Delete" variant="secondary" onPress={remove} />
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: space(4), gap: space(4) },
  label: { fontSize: 12, fontWeight: '500', color: colors.textMuted, textTransform: 'uppercase', letterSpacing: 0.5 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space(2) },
  card: { backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border, padding: space(4), gap: space(2) },
  itemRow: { flexDirection: 'row', justifyContent: 'space-between', gap: space(3) },
  itemName: { flex: 1, fontSize: 14, color: colors.text },
  itemPrice: { fontSize: 14, color: colors.text, fontVariant: ['tabular-nums'] },
  actions: { gap: space(3), marginTop: space(2) },
});
