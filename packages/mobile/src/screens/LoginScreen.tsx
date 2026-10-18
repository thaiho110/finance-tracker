import React, { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { isValidEmail } from '@finance-tracker/shared';
import { useSession } from '../session/SessionContext';
import { Banner, Button, Field } from '../components/ui';
import { colors, space } from '../theme';

export function LoginScreen() {
  const { signIn, server: savedServer } = useSession();
  const [server, setServer] = useState(savedServer);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => setServer(savedServer), [savedServer]);

  const submit = async () => {
    setError(null);
    setBusy(true);
    try {
      await signIn(server, email, password);
    } catch (err) {
      const message = err instanceof Error ? err.message : '';
      setError(message === 'Network request failed'
        ? `Can't reach ${server}. Check the server address and that your phone is on the same network.`
        : message || 'Sign-in failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <View style={styles.brand}>
          <View style={styles.mark}><Text style={styles.markText}>₫</Text></View>
          <Text style={styles.title}>Finance Tracker</Text>
          <Text style={styles.subtitle}>Sign in to your account</Text>
        </View>
        {error ? <Banner tone="danger">{error}</Banner> : null}
        <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" autoComplete="email" keyboardType="email-address" />
        <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry autoComplete="password" />
        <Field
          label="Server"
          value={server}
          onChangeText={setServer}
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="url"
          hint="On a phone, use your computer's network address, e.g. http://192.168.1.20:8080"
        />
        <Button title="Sign in" onPress={submit} loading={busy} disabled={!isValidEmail(email) || !password || !server.trim()} />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.canvas },
  container: { padding: space(6), gap: space(4), paddingTop: space(16) },
  brand: { alignItems: 'center', gap: space(2), marginBottom: space(4) },
  mark: { width: 56, height: 56, borderRadius: 14, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  markText: { color: colors.onPrimary, fontSize: 28, fontWeight: '700' },
  title: { fontSize: 24, fontWeight: '700', color: colors.text },
  subtitle: { fontSize: 15, color: colors.textMuted },
});
