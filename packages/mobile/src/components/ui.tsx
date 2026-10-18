import React from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextInputProps,
  type ViewStyle,
} from 'react-native';
import { colors, radius, space } from '../theme';

type Variant = 'primary' | 'secondary' | 'danger';

export function Button({ title, onPress, variant = 'primary', disabled, loading, style }: {
  title: string;
  onPress: () => void;
  variant?: Variant;
  disabled?: boolean;
  loading?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const inactive = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!inactive }}
      onPress={onPress}
      disabled={inactive}
      style={({ pressed }) => [styles.button, styles[variant], pressed && styles.pressed, inactive && styles.disabled, style]}
    >
      {loading
        ? <ActivityIndicator color={variant === 'secondary' ? colors.text : colors.onPrimary} />
        : <Text style={[styles.buttonText, variant === 'secondary' && styles.secondaryText]}>{title}</Text>}
    </Pressable>
  );
}

export function Field({ label, hint, invalid, ...input }: TextInputProps & { label: string; hint?: string; invalid?: boolean }) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        placeholderTextColor={colors.textMuted}
        style={[styles.input, invalid && styles.inputInvalid]}
        {...input}
      />
      {hint ? <Text style={styles.hint}>{hint}</Text> : null}
    </View>
  );
}

export function Banner({ tone, children }: { tone: 'warning' | 'danger' | 'info'; children: React.ReactNode }) {
  const palette = {
    warning: { bg: colors.warningSoft, fg: colors.warning },
    danger: { bg: colors.dangerSoft, fg: colors.danger },
    info: { bg: colors.muted, fg: colors.textMuted },
  }[tone];
  return (
    <View style={[styles.banner, { backgroundColor: palette.bg }]}>
      <Text style={[styles.bannerText, { color: palette.fg }]}>{children}</Text>
    </View>
  );
}

export function Chip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.chip, selected && styles.chipSelected]} accessibilityState={{ selected }}>
      <Text style={[styles.chipText, selected && styles.chipTextSelected]}>{label}</Text>
    </Pressable>
  );
}

export function Centered({ children }: { children: React.ReactNode }) {
  return <View style={styles.centered}>{children}</View>;
}

const styles = StyleSheet.create({
  button: { height: 44, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', paddingHorizontal: space(4) },
  primary: { backgroundColor: colors.primary },
  secondary: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border },
  danger: { backgroundColor: colors.danger },
  pressed: { opacity: 0.85 },
  disabled: { opacity: 0.5 },
  buttonText: { color: colors.onPrimary, fontSize: 15, fontWeight: '600' },
  secondaryText: { color: colors.text },
  field: { gap: space(1.5) },
  label: { fontSize: 12, fontWeight: '500', color: colors.textMuted, textTransform: 'uppercase', letterSpacing: 0.5 },
  input: {
    height: 44, borderRadius: radius.md, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface,
    paddingHorizontal: space(3), fontSize: 16, color: colors.text,
  },
  inputInvalid: { borderColor: colors.danger },
  hint: { fontSize: 12, color: colors.textMuted },
  banner: { borderRadius: radius.md, padding: space(3) },
  bannerText: { fontSize: 14 },
  chip: {
    borderRadius: 999, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface,
    paddingHorizontal: space(3), paddingVertical: space(1.5),
  },
  chipSelected: { backgroundColor: colors.primarySoft, borderColor: colors.primary },
  chipText: { fontSize: 13, color: colors.text },
  chipTextSelected: { color: colors.primary, fontWeight: '600' },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: space(6), gap: space(3) },
});
