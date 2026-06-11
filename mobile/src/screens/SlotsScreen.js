import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity,
  ActivityIndicator, Alert, RefreshControl,
} from 'react-native';
import { useApp } from '../context/AppContext';
import { CANADA_FACILITIES, VISA_TYPES } from '../api/aisClient';
import Picker from '../components/Picker';
import { colors, globalStyles as g } from '../utils/theme';

const LOCATION_OPTIONS = Object.entries(CANADA_FACILITIES).map(([id, f]) => ({
  value: id,
  label: f.name,
  group: 'Canada (Live)',
}));

const VISA_OPTIONS = VISA_TYPES.map(v => ({ value: v.id, label: v.label }));

function formatDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-CA', { weekday: 'short', month: 'short', day: 'numeric' });
}

export default function SlotsScreen() {
  const { aisStatus, getClient, addBooking } = useApp();
  const [location, setLocation] = useState('');
  const [visaType, setVisaType] = useState('');
  const [slots, setSlots]       = useState([]);
  const [loading, setLoading]   = useState(false);
  const [checked, setChecked]   = useState(false);
  const [bookingId, setBookingId] = useState(null); // slot id being booked

  const isConnected = aisStatus === 'connected';

  async function handleCheck() {
    if (!location || !visaType) {
      Alert.alert('Missing fields', 'Please select a location and visa type.');
      return;
    }
    if (!isConnected) {
      Alert.alert('Not Connected', 'Connect your AIS account first (Home tab).');
      return;
    }
    setLoading(true);
    setChecked(false);
    setSlots([]);

    try {
      const client = getClient();
      const facility = CANADA_FACILITIES[location];
      const dates = await client.getAvailableDates(facility.id);
      const result = [];

      for (const d of dates.slice(0, 30)) {
        if (!d.date) continue;
        const times = await client.getAvailableTimes(facility.id, d.date);
        if (times.length > 0) {
          for (const t of times) {
            result.push({ date: d.date, time: t, facilityId: facility.id, facilityName: facility.name });
          }
        }
      }
      setSlots(result);
      setChecked(true);
    } catch (err) {
      Alert.alert('Error', err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleBook(slot) {
    Alert.alert(
      'Confirm Booking',
      `Book appointment at ${slot.facilityName}?\n\nDate: ${formatDate(slot.date)}\nTime: ${slot.time}`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Book Now',
          style: 'default',
          onPress: async () => {
            setBookingId(`${slot.date}-${slot.time}`);
            try {
              const client = getClient();
              const result = await client.bookAppointment(slot.facilityId, slot.date, slot.time);

              const booking = addBooking({
                facilityId: slot.facilityId,
                location: slot.facilityName,
                visaType,
                date: slot.date,
                time: slot.time,
                status: result.success ? 'booked' : 'failed',
                result,
              });

              if (result.success) {
                Alert.alert('Booked!', `Appointment confirmed at ${slot.facilityName} on ${formatDate(slot.date)} at ${slot.time}`);
              } else {
                Alert.alert('Booking Failed', 'AIS did not confirm the booking. Please try manually on the AIS website.');
              }
            } catch (err) {
              Alert.alert('Error', err.message);
            } finally {
              setBookingId(null);
            }
          },
        },
      ]
    );
  }

  const available = slots;
  const locName = CANADA_FACILITIES[location]?.name;

  return (
    <ScrollView style={g.screen} contentContainerStyle={{ padding: 20 }}>

      {/* Status banner */}
      {!isConnected && (
        <View style={styles.warnBanner}>
          <Text style={styles.warnText}>Connect your AIS account (Home tab) to check live slots.</Text>
        </View>
      )}

      {/* Selection */}
      <View style={g.card}>
        <Text style={g.cardTitle}>Check Available Slots</Text>
        <Text style={g.cardSub}>Select a Canadian consulate and visa type to see real-time availability.</Text>

        <Picker
          label="Consulate"
          value={location}
          options={LOCATION_OPTIONS}
          placeholder="Select city"
          onChange={setLocation}
        />
        <Picker
          label="Visa Type"
          value={visaType}
          options={VISA_OPTIONS}
          placeholder="Select visa type"
          onChange={setVisaType}
        />

        <TouchableOpacity
          style={[g.btn, g.btnPrimary, loading && { opacity: 0.6 }]}
          onPress={handleCheck}
          disabled={loading}
        >
          {loading
            ? <ActivityIndicator color="#fff" />
            : <Text style={g.btnPrimaryText}>Check Now</Text>
          }
        </TouchableOpacity>
      </View>

      {/* Results */}
      {checked && (
        <View style={g.card}>
          <View style={[g.row, { marginBottom: 12 }]}>
            <Text style={g.cardTitle}>{locName} — {visaType}</Text>
            <View style={g.spacer} />
            <View style={[g.badge, available.length > 0 ? g.badgeGreen : g.badgeGray]}>
              <Text style={available.length > 0 ? g.badgeGreenText : g.badgeGrayText}>
                {available.length} slot{available.length !== 1 ? 's' : ''}
              </Text>
            </View>
          </View>

          {available.length === 0 ? (
            <Text style={g.emptyState}>No open slots found. Check back later.</Text>
          ) : (
            available.map((slot, i) => {
              const isBooking = bookingId === `${slot.date}-${slot.time}`;
              return (
                <View key={i} style={styles.slotRow}>
                  <View>
                    <Text style={styles.slotDate}>{formatDate(slot.date)}</Text>
                    <Text style={styles.slotTime}>{slot.time}</Text>
                  </View>
                  <TouchableOpacity
                    style={[styles.bookBtn, isBooking && { opacity: 0.5 }]}
                    onPress={() => handleBook(slot)}
                    disabled={!!bookingId}
                  >
                    {isBooking
                      ? <ActivityIndicator color="#fff" size="small" />
                      : <Text style={styles.bookBtnText}>Book</Text>
                    }
                  </TouchableOpacity>
                </View>
              );
            })
          )}
        </View>
      )}

    </ScrollView>
  );
}

const styles = StyleSheet.create({
  warnBanner: {
    backgroundColor: '#fffbeb',
    borderWidth: 1.5,
    borderColor: '#fcd34d',
    borderRadius: 10,
    padding: 12,
    marginBottom: 16,
  },
  warnText: { color: '#92400e', fontSize: 13, fontWeight: '500' },

  slotRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.gray100,
  },
  slotDate: { fontSize: 14, fontWeight: '700', color: colors.gray900 },
  slotTime: { fontSize: 12, color: colors.gray500, marginTop: 2 },

  bookBtn: {
    backgroundColor: colors.blue,
    paddingHorizontal: 18,
    paddingVertical: 8,
    borderRadius: 8,
    minWidth: 70,
    alignItems: 'center',
  },
  bookBtnText: { color: '#fff', fontWeight: '700', fontSize: 13 },
});
