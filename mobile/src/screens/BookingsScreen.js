import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Linking } from 'react-native';
import { useApp } from '../context/AppContext';
import { CANADA_FACILITIES } from '../api/aisClient';
import { colors, globalStyles as g } from '../utils/theme';

function formatDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-CA', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
}

function formatTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString('en-CA', { hour: '2-digit', minute: '2-digit' });
}

export default function BookingsScreen() {
  const { bookings, notifications } = useApp();
  const sorted = [...bookings].reverse();

  return (
    <ScrollView style={g.screen} contentContainerStyle={{ padding: 20 }}>

      {/* Recent notifications */}
      {notifications.length > 0 && (
        <View style={g.card}>
          <Text style={g.cardTitle}>Notifications</Text>
          {notifications.slice(0, 5).map(n => (
            <View key={n.id} style={styles.notifRow}>
              <Text style={styles.notifMsg}>{n.msg}</Text>
              <Text style={styles.notifTime}>{formatTime(n.time)}</Text>
            </View>
          ))}
        </View>
      )}

      {/* Bookings */}
      <Text style={styles.sectionTitle}>Booking History ({bookings.length})</Text>

      {sorted.length === 0 ? (
        <Text style={g.emptyState}>No bookings yet. Check slots and tap Book.</Text>
      ) : (
        sorted.map(b => {
          const facilityName = CANADA_FACILITIES[b.location]?.name || b.location || 'Unknown';
          const statusStyle = b.status === 'booked'
            ? [g.badge, g.badgeGreen] : b.status === 'failed'
            ? [g.badge, g.badgeRed] : [g.badge, g.badgeGray];
          const statusTextStyle = b.status === 'booked'
            ? g.badgeGreenText : b.status === 'failed'
            ? g.badgeRedText : g.badgeGrayText;

          return (
            <View key={b.id} style={styles.bookingCard}>
              <View style={[g.row, { marginBottom: 8 }]}>
                <Text style={styles.bookingTitle}>{facilityName}</Text>
                <View style={g.spacer} />
                <View style={statusStyle}>
                  <Text style={statusTextStyle}>{b.status}</Text>
                </View>
                {b.autoBooked && (
                  <View style={[g.badge, g.badgeBlue, { marginLeft: 6 }]}>
                    <Text style={g.badgeBlueText}>Auto</Text>
                  </View>
                )}
              </View>

              <Text style={styles.bookingVisa}>{b.visaType}</Text>
              <Text style={styles.bookingDate}>{formatDate(b.date)} at {b.time}</Text>
              <Text style={styles.bookingMeta}>Attempted: {formatDate(b.createdAt)} {formatTime(b.createdAt)}</Text>

              {b.result?.confirmationUrl && (
                <TouchableOpacity
                  style={styles.viewLink}
                  onPress={() => Linking.openURL(b.result.confirmationUrl)}
                >
                  <Text style={styles.viewLinkText}>View on AIS →</Text>
                </TouchableOpacity>
              )}
            </View>
          );
        })
      )}

    </ScrollView>
  );
}

const styles = StyleSheet.create({
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: colors.gray700,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 10,
  },
  notifRow: {
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.gray100,
  },
  notifMsg: { fontSize: 13, fontWeight: '600', color: colors.gray900 },
  notifTime: { fontSize: 11, color: colors.gray500, marginTop: 2 },

  bookingCard: {
    backgroundColor: colors.white,
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1.5,
    borderColor: colors.gray200,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 2,
  },
  bookingTitle: { fontSize: 15, fontWeight: '700', color: colors.gray900, flex: 1 },
  bookingVisa: { fontSize: 13, color: colors.gray500, marginBottom: 6 },
  bookingDate: { fontSize: 14, fontWeight: '600', color: colors.gray900, marginBottom: 2 },
  bookingMeta: { fontSize: 12, color: colors.gray500, marginBottom: 8 },
  viewLink: { alignSelf: 'flex-start' },
  viewLinkText: { fontSize: 13, color: colors.blue, fontWeight: '600' },
});
