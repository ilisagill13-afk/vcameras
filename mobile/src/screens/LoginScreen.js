import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  ScrollView, ActivityIndicator, Alert, KeyboardAvoidingView, Platform,
} from 'react-native';
import { useApp } from '../context/AppContext';
import { colors, globalStyles as g } from '../utils/theme';

export default function LoginScreen() {
  const { aisStatus, aisError, credentials, appointmentId, connectAIS, disconnectAIS } = useApp();
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading]   = useState(false);

  const isConnected = aisStatus === 'connected';

  async function handleConnect() {
    if (!email.trim() || !password) return;
    setLoading(true);
    const ok = await connectAIS(email.trim(), password);
    setLoading(false);
    if (!ok) Alert.alert('Connection Failed', aisError || 'Check your credentials and try again.');
  }

  async function handleDisconnect() {
    Alert.alert('Disconnect', 'Remove AIS credentials from this device?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Disconnect', style: 'destructive', onPress: disconnectAIS },
    ]);
  }

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
      <ScrollView style={g.screen} contentContainerStyle={{ padding: 20 }}>

        {/* Header */}
        <View style={styles.hero}>
          <Text style={styles.heroFlag}>🇺🇸 🇨🇦</Text>
          <Text style={styles.heroTitle}>US Visa Bot</Text>
          <Text style={styles.heroSub}>Canada — AIS Appointment Scheduler</Text>
        </View>

        {/* Connected state */}
        {isConnected ? (
          <View style={styles.connectedCard}>
            <View style={styles.connRow}>
              <View style={[styles.dot, { backgroundColor: colors.green }]} />
              <Text style={styles.connLabel}>Connected to AIS</Text>
            </View>
            <Text style={styles.connEmail}>{credentials?.email}</Text>
            {appointmentId && (
              <Text style={styles.connMeta}>Appointment ID: {appointmentId}</Text>
            )}
            <TouchableOpacity style={[g.btn, styles.disconnectBtn]} onPress={handleDisconnect}>
              <Text style={styles.disconnectText}>Disconnect</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View style={g.card}>
            <Text style={g.cardTitle}>Connect AIS Account</Text>
            <Text style={g.cardSub}>
              Enter your ais.usvisa-info.com credentials to enable live slot checking and auto-booking.
            </Text>

            <Text style={g.label}>AIS Email</Text>
            <TextInput
              style={g.input}
              value={email}
              onChangeText={setEmail}
              placeholder="your@email.com"
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
            />

            <Text style={[g.label, { marginTop: 14 }]}>AIS Password</Text>
            <TextInput
              style={g.input}
              value={password}
              onChangeText={setPassword}
              placeholder="••••••••"
              secureTextEntry
            />

            {aisError && aisStatus === 'error' && (
              <View style={styles.errorBox}>
                <Text style={styles.errorText}>{aisError}</Text>
              </View>
            )}

            <TouchableOpacity
              style={[g.btn, g.btnPrimary, { marginTop: 18 }]}
              onPress={handleConnect}
              disabled={loading}
            >
              {loading
                ? <ActivityIndicator color="#fff" />
                : <Text style={g.btnPrimaryText}>Connect to AIS Portal</Text>
              }
            </TouchableOpacity>
          </View>
        )}

        {/* Info box */}
        <View style={styles.infoBox}>
          <Text style={styles.infoTitle}>How it works</Text>
          <Text style={styles.infoLine}>1. Connect your AIS account</Text>
          <Text style={styles.infoLine}>2. Check real-time slot availability</Text>
          <Text style={styles.infoLine}>3. Book with one tap — or enable Auto-Book</Text>
          <Text style={styles.infoLine}>4. Get notified the moment a slot opens</Text>
        </View>

      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  hero: { alignItems: 'center', paddingVertical: 32 },
  heroFlag: { fontSize: 40, marginBottom: 10 },
  heroTitle: { fontSize: 26, fontWeight: '800', color: colors.gray900, letterSpacing: -0.5 },
  heroSub: { fontSize: 14, color: colors.gray500, marginTop: 4 },

  connectedCard: {
    backgroundColor: '#ecfdf5',
    borderWidth: 1.5,
    borderColor: '#6ee7b7',
    borderRadius: 12,
    padding: 18,
    marginBottom: 16,
  },
  connRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  dot: { width: 9, height: 9, borderRadius: 5, marginRight: 8 },
  connLabel: { fontSize: 14, fontWeight: '700', color: '#065f46' },
  connEmail: { fontSize: 15, fontWeight: '600', color: colors.gray900, marginBottom: 2 },
  connMeta: { fontSize: 12, color: colors.gray500, marginBottom: 14 },
  disconnectBtn: { backgroundColor: colors.gray100, alignSelf: 'flex-start' },
  disconnectText: { fontSize: 13, fontWeight: '600', color: colors.gray700 },

  errorBox: { backgroundColor: '#fef2f2', borderRadius: 8, padding: 12, marginTop: 12 },
  errorText: { color: colors.red, fontSize: 13 },

  infoBox: { backgroundColor: colors.blue + '12', borderRadius: 12, padding: 16, marginTop: 8 },
  infoTitle: { fontSize: 13, fontWeight: '700', color: colors.blue, marginBottom: 8, textTransform: 'uppercase', letterSpacing: 0.5 },
  infoLine: { fontSize: 13, color: colors.gray700, marginBottom: 4 },
});
