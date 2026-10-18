import React from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSession } from '../session/SessionContext';
import { Button } from '../components/ui';
import { colors, radius, space } from '../theme';

const ROLE_NAMES: Record<string, string> = { USER: 'User', ADMIN: 'Admin', SUPER_ADMIN: 'Super admin' };

export function SettingsScreen() {
  const { user, server, signOut } = useSession();
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <View style={styles.card}>
        <Row label="Signed in as" value={user?.email ?? '—'} />
        <Row label="Role" value={user ? ROLE_NAMES[user.role] ?? user.role : '—'} />
        <Row label="Server" value={server} />
      </View>
      <Text style={styles.note}>
        Account administration and the role table are on the web app. Features your account doesn't include
        are hidden here.
      </Text>
      <Button title="Sign out" variant="secondary" onPress={() => void signOut()} />
    </ScrollView>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.value} numberOfLines={1}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: space(4), gap: space(4) },
  card: { backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border, paddingHorizontal: space(4) },
  row: { paddingVertical: space(3), gap: space(1), borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.border },
  label: { fontSize: 12, color: colors.textMuted, textTransform: 'uppercase', letterSpacing: 0.5 },
  value: { fontSize: 16, color: colors.text },
  note: { fontSize: 13, color: colors.textMuted },
});
