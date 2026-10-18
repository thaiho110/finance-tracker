import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import { formatVnd, type Transaction } from '@finance-tracker/shared';
import { useSession } from '../session/SessionContext';
import { Banner, Centered } from '../components/ui';
import { colors, radius, space } from '../theme';
import type { TransactionsStackParams } from '../navigation/types';

const PAGE_SIZE = 25;
const SOURCE_LABELS: Record<string, string> = { CSV: 'CSV', OCR: 'Receipt', MANUAL: 'Manual' };

type Props = NativeStackScreenProps<TransactionsStackParams, 'TransactionList'>;

export function TransactionsScreen({ navigation }: Props) {
  const { api } = useSession();
  const [items, setItems] = useState<Transaction[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (nextPage: number) => {
    try {
      const result = await api.transactions.list({ page: nextPage, size: PAGE_SIZE, sort: 'date,desc' });
      setItems((current) => (nextPage === 0 ? result.content : [...current, ...result.content]));
      setPage(nextPage);
      setTotalPages(result.totalPages);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load transactions');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [api]);

  // Reload when coming back from a scan, an import or an edit.
  useFocusEffect(useCallback(() => {
    void load(0);
  }, [load]));

  useEffect(() => {
    navigation.setOptions({ title: 'Transactions' });
  }, [navigation]);

  if (loading) {
    return <Centered><ActivityIndicator color={colors.primary} /></Centered>;
  }

  return (
    <FlatList
      style={styles.list}
      contentContainerStyle={items.length === 0 ? styles.emptyContainer : styles.content}
      data={items}
      keyExtractor={(item) => item.id}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); void load(0); }} />}
      onEndReachedThreshold={0.4}
      onEndReached={() => {
        if (page + 1 < totalPages) void load(page + 1);
      }}
      ListHeaderComponent={error ? <Banner tone="danger">{error}</Banner> : undefined}
      ListEmptyComponent={
        <Centered>
          <Text style={styles.emptyTitle}>No transactions yet</Text>
          <Text style={styles.muted}>Scan a receipt or import a bank statement to get started.</Text>
        </Centered>
      }
      ItemSeparatorComponent={() => <View style={styles.separator} />}
      renderItem={({ item }) => (
        <Pressable style={styles.row} onPress={() => navigation.navigate('TransactionDetail', { id: item.id })}>
          <View style={styles.rowMain}>
            <Text style={styles.merchant} numberOfLines={1}>{item.cleanMerchant}</Text>
            <View style={styles.meta}>
              <View style={[styles.dot, { backgroundColor: item.categoryColor || colors.textMuted }]} />
              <Text style={styles.muted}>{item.date} · {item.category} · {SOURCE_LABELS[item.sourceType] ?? item.sourceType}</Text>
            </View>
          </View>
          <Text style={styles.amount}>{formatVnd(item.amount)}</Text>
        </Pressable>
      )}
    />
  );
}

const styles = StyleSheet.create({
  meta: { flexDirection: 'row', alignItems: 'center', gap: space(1.5) },
  dot: { width: 8, height: 8, borderRadius: 4 },
  list: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: space(4), gap: space(3) },
  emptyContainer: { flexGrow: 1 },
  row: {
    flexDirection: 'row', alignItems: 'center', gap: space(3), padding: space(4),
    backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border,
  },
  rowMain: { flex: 1, gap: space(1) },
  merchant: { fontSize: 16, fontWeight: '600', color: colors.text },
  amount: { fontSize: 16, fontWeight: '600', color: colors.text, fontVariant: ['tabular-nums'] },
  muted: { fontSize: 13, color: colors.textMuted, textAlign: 'left' },
  emptyTitle: { fontSize: 17, fontWeight: '600', color: colors.text },
  separator: { height: space(2) },
});
