import React from 'react';
import { ActivityIndicator, Text } from 'react-native';
import { NavigationContainer, DefaultTheme, type Theme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { useSession } from '../session/SessionContext';
import { Centered } from '../components/ui';
import { LoginScreen } from '../screens/LoginScreen';
import { OverviewScreen } from '../screens/OverviewScreen';
import { TransactionsScreen } from '../screens/TransactionsScreen';
import { TransactionDetailScreen } from '../screens/TransactionDetailScreen';
import { ScanReceiptScreen } from '../screens/ScanReceiptScreen';
import { ImportCsvScreen } from '../screens/ImportCsvScreen';
import { SettingsScreen } from '../screens/SettingsScreen';
import { colors } from '../theme';
import type { TabParams, TransactionsStackParams } from './types';

const Stack = createNativeStackNavigator<TransactionsStackParams>();
const Tabs = createBottomTabNavigator<TabParams>();

const theme: Theme = {
  ...DefaultTheme,
  colors: { ...DefaultTheme.colors, primary: colors.primary, background: colors.canvas, card: colors.surface, text: colors.text, border: colors.border },
};

const headerStyle = { headerStyle: { backgroundColor: colors.surface }, headerTitleStyle: { color: colors.text } };

function TransactionsStack() {
  return (
    <Stack.Navigator screenOptions={headerStyle}>
      <Stack.Screen name="TransactionList" component={TransactionsScreen} options={{ title: 'Transactions' }} />
      <Stack.Screen name="TransactionDetail" component={TransactionDetailScreen} options={{ title: 'Transaction' }} />
    </Stack.Navigator>
  );
}

const tabIcon = (glyph: string) => ({ color }: { color: string }) => <Text style={{ color, fontSize: 18 }}>{glyph}</Text>;

/** Tabs follow the user's permissions (ADR-018); the server enforces them either way. */
function MainTabs() {
  const { can } = useSession();
  return (
    <Tabs.Navigator screenOptions={{ ...headerStyle, tabBarActiveTintColor: colors.primary, tabBarInactiveTintColor: colors.textMuted }}>
      {can('DASHBOARD_VIEW') && (
        <Tabs.Screen name="Overview" component={OverviewScreen} options={{ title: 'Overview', tabBarIcon: tabIcon('◔') }} />
      )}
      {can('TRANSACTION_READ') && (
        <Tabs.Screen name="Transactions" component={TransactionsStack} options={{ headerShown: false, tabBarIcon: tabIcon('≡') }} />
      )}
      {can('RECEIPT_SCAN') && (
        <Tabs.Screen name="Scan" component={ScanReceiptScreen} options={{ title: 'Scan receipt', tabBarLabel: 'Scan', tabBarIcon: tabIcon('◎') }} />
      )}
      {can('CSV_IMPORT') && (
        <Tabs.Screen name="Import" component={ImportCsvScreen} options={{ title: 'Import CSV', tabBarLabel: 'Import', tabBarIcon: tabIcon('⇪') }} />
      )}
      <Tabs.Screen name="Settings" component={SettingsScreen} options={{ tabBarIcon: tabIcon('⚙') }} />
    </Tabs.Navigator>
  );
}

export function RootNavigator() {
  const { status } = useSession();
  if (status === 'starting') {
    return <Centered><ActivityIndicator color={colors.primary} /></Centered>;
  }
  return (
    <NavigationContainer theme={theme}>
      {status === 'signedIn' ? <MainTabs /> : <LoginScreen />}
    </NavigationContainer>
  );
}
