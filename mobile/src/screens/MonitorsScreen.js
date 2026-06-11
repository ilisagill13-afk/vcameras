import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity,
  Switch, Alert, ActivityIndicator,
} from 'react-native';
import { useApp } from '../context/AppContext';
import { CANADA_FACILITIES, VISA_TYPES } from '../api/aisClient';
import Picker from '../components/Picker';
import { colors, globalStyles as g } from '../utils/theme';

const LOCATION_OPTIONS = Object.entries(CANADA_FACILITIES).map(([id, f]) => ({
  value: id, label: f.name,
}));
const VISA_OPTIONS = VISA_TYPES.map(v => ({ value: v.id, label: v.label }));

function timeAgo(iso) {
  if (!iso) return 'Never';
  const d = Math.floor((Date.now() - new Date(iso)) / 1000);
  if (d < 60) return 'just now';
  if (d < 3600) return `${Math.floor(d / 60)}m ago`;
  if (d < 86400) return `${Math.floor(d / 3600)}h ago`;
  return `${Math.floor(d / 86400)}d ago`;
}

function MonitorCard({ monitor, onToggle, onDelete }) {
  const facilityName = CANADA_FACILITIES[monitor.location]?.name || monitor.location;
  return (
    <View style={styles.monitorCard}>
      <View style={g.row}>
        <View style={[styles.dot, { backgroundColor: monitor.active ? colors.green : colors.gray500 }]} />
        <Text style={styles.monTitle}>{facilityName} — {monitor.visaType}</Text>
      </View>

      <View style={[g.row, { marginTop: 6, flexWrap: 'wrap', gap: 6 }]}>
        {monitor.autoBook && (
          <View style={[g.badge, g.badgeBlue]}>
            <Text style={g.badgeBlueText}>Auto-Book ON</Text>
          </View>
        )}
        {monitor.slotsFound > 0 && (
          <View style={[g.badge, g.badgeGreen]}>
            <Text style={g.badgeGreenText}>{monitor.slotsFound} found</Text>
          </View>
        )}
        <Text style={styles.metaText}>Checked: {timeAgo(monitor.lastChecked)}</Text>
      </View>

      <View style={[g.row, { marginTop: 12, gap: 8 }]}>
        <TouchableOpacity style={[g.btn, g.btnGhost, { height: 34, flex: 1 }]} onPress={onToggle}>
          <Text style={g.btnGhostText}>{monitor.active ? 'Pause' : 'Resume'}</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[g.btn, g.btnDanger, { height: 34, flex: 1 }]} onPress={onDelete}>
          <Text style={g.btnDangerText}>Remove</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

export default function MonitorsScreen() {
  const { monitors, aisStatus, addMonitor, removeMonitor, toggleMonitor, getClient, addBooking, pushNotification, updateMonitor } = useApp();
  const [location, setLocation]   = useState('');
  const [visaType, setVisaType]   = useState('');
  const [autoBook, setAutoBook]   = useState(false);
  const [adding, setAdding]       = useState(false);
  const [runningId, setRunningId] = useState(null);

  const isConnected = aisStatus === 'connected';

  function handleAdd() {
    if (!location || !visaType) {
      Alert.alert('Missing fields', 'Select a location and visa type.');
      return;
    }
    addMonitor({ location, visaType, autoBook });
    setLocation('');
    setVisaType('');
    setAutoBook(false);
  }

  async function handleRunNow(monitor) {
    if (!isConnected) {
      Alert.alert('Not Connected', 'Connect your AIS account first.');
      return;
    }
    setRunningId(monitor.id);
    try {
      const client   = getClient();
      const facility = CANADA_FACILITIES[monitor.location];
      const dates    = await client.getAvailableDates(facility.id);

      let found = 0;
      for (const d of dates.slice(0, 20)) {
        if (!d.date) continue;
        const times = await client.getAvailableTimes(facility.id, d.date);
        if (times.length > 0) {
          found += times.length;

          if (monitor.autoBook) {
            const result = await client.bookAppointment(facility.id, d.date, times[0]);
            addBooking({ facilityId: facility.id, location: monitor.location, visaType: monitor.visaType, date: d.date, time: times[0], status: result.success ? 'booked' : 'failed', result, autoBooked: true });
            if (result.success) {
              pushNotification(`AUTO-BOOKED: ${facility.name} on ${d.date} at ${times[0]}`);
              Alert.alert('Booked!', `Auto-booked at ${facility.name} on ${d.date} at ${times[0]}`);
              updateMonitor(monitor.id, { active: false, slotsFound: found, lastChecked: new Date().toISOString() });
              setRunningId(null);
              return;
            }
          }
        }
      }

      updateMonitor(monitor.id, { slotsFound: found, lastChecked: new Date().toISOString() });

      if (found > 0) {
        pushNotification(`${found} slot(s) found at ${facility.name} for ${monitor.visaType}`);
        Alert.alert('Slots Found!', `${found} slot(s) available at ${facility.name}. Go to Check Slots tab to book.`);
      } else {
        Alert.alert('No Slots', 'No open slots found right now.');
      }
    } catch (err) {
      Alert.alert('Error', err.message);
    } finally {
      setRunningId(null);
    }
  }

  return (
    <ScrollView style={g.screen} contentContainerStyle={{ padding: 20 }}>

      {/* Add monitor form */}
      <View style={g.card}>
        <Text style={g.cardTitle}>Add Monitor</Text>
        <Text style={g.cardSub}>The bot will check every 5 minutes and notify you when slots open.</Text>

        <Picker label="Consulate" value={location} options={LOCATION_OPTIONS} placeholder="Select city" onChange={setLocation} />
        <Picker label="Visa Type" value={visaType} options={VISA_OPTIONS} placeholder="Select visa type" onChange={setVisaType} />

        <View style={styles.switchRow}>
          <View style={{ flex: 1 }}>
            <Text style={styles.switchLabel}>Auto-Book</Text>
            <Text style={styles.switchSub}>Book first available slot automatically</Text>
          </View>
          <Switch
            value={autoBook}
            onValueChange={setAutoBook}
            trackColor={{ true: colors.blue }}
            thumbColor={autoBook ? colors.white : colors.gray200}
          />
        </View>

        <TouchableOpacity style={[g.btn, g.btnPrimary, { marginTop: 16 }]} onPress={handleAdd}>
          <Text style={g.btnPrimaryText}>Add Monitor</Text>
        </TouchableOpacity>
      </View>

      {/* Monitor list */}
      <Text style={styles.sectionTitle}>{monitors.length} Active Monitor{monitors.length !== 1 ? 's' : ''}</Text>

      {monitors.length === 0 ? (
        <Text style={g.emptyState}>No monitors yet. Add one above.</Text>
      ) : (
        monitors.map(m => (
          <View key={m.id}>
            <MonitorCard
              monitor={m}
              onToggle={() => toggleMonitor(m.id)}
              onDelete={() => Alert.alert('Remove Monitor', 'Delete this monitor?', [
                { text: 'Cancel', style: 'cancel' },
                { text: 'Remove', style: 'destructive', onPress: () => removeMonitor(m.id) },
              ])}
            />
            <TouchableOpacity
              style={[styles.runBtn, runningId === m.id && { opacity: 0.5 }]}
              onPress={() => handleRunNow(m)}
              disabled={runningId !== null}
            >
              {runningId === m.id
                ? <ActivityIndicator color={colors.blue} size="small" />
                : <Text style={styles.runBtnText}>Check Now</Text>
              }
            </TouchableOpacity>
          </View>
        ))
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
    marginTop: 4,
  },
  monitorCard: {
    backgroundColor: colors.white,
    borderRadius: 12,
    padding: 16,
    borderWidth: 1.5,
    borderColor: colors.gray200,
    marginBottom: 4,
  },
  dot: { width: 9, height: 9, borderRadius: 5, marginRight: 8, flexShrink: 0 },
  monTitle: { fontSize: 14, fontWeight: '700', color: colors.gray900, flex: 1 },
  metaText: { fontSize: 12, color: colors.gray500 },

  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.gray50,
    borderRadius: 10,
    padding: 14,
    marginTop: 8,
  },
  switchLabel: { fontSize: 14, fontWeight: '600', color: colors.gray900 },
  switchSub: { fontSize: 12, color: colors.gray500, marginTop: 2 },

  runBtn: {
    alignItems: 'center',
    paddingVertical: 10,
    marginBottom: 14,
  },
  runBtnText: { fontSize: 13, color: colors.blue, fontWeight: '600' },
});
