import React, { useEffect, useRef } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Text, View, AppState } from 'react-native';
import * as Notifications from 'expo-notifications';
import * as BackgroundFetch from 'expo-background-fetch';
import * as TaskManager from 'expo-task-manager';

import { AppProvider, useApp } from './src/context/AppContext';
import { CANADA_FACILITIES } from './src/api/aisClient';
import LoginScreen    from './src/screens/LoginScreen';
import SlotsScreen    from './src/screens/SlotsScreen';
import MonitorsScreen from './src/screens/MonitorsScreen';
import BookingsScreen from './src/screens/BookingsScreen';
import { colors } from './src/utils/theme';

const Tab = createBottomTabNavigator();
const BG_TASK = 'VISA_SLOT_CHECK';

// Configure how notifications look when app is foregrounded
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge:  true,
  }),
});

async function sendLocalNotif(title, body) {
  await Notifications.scheduleNotificationAsync({
    content: { title, body, sound: true },
    trigger: null,
  });
}

// Tab bar icon — simple emoji fallback (no extra icon library needed)
function TabIcon({ label, focused }) {
  const icons = { Home: '🏠', Slots: '📅', Monitors: '🔔', Bookings: '📋' };
  return (
    <Text style={{ fontSize: 20, opacity: focused ? 1 : 0.45 }}>{icons[label]}</Text>
  );
}

function Navigator() {
  const { monitors, aisStatus, getClient, addBooking, pushNotification, updateMonitor } = useApp();
  const appState = useRef(AppState.currentState);

  // Request notification permission on mount
  useEffect(() => {
    (async () => {
      const { status } = await Notifications.requestPermissionsAsync();
      if (status !== 'granted') console.warn('Notification permission not granted');
    })();
  }, []);

  // Foreground slot-check loop (every 5 minutes when app is active)
  useEffect(() => {
    let interval;

    async function runCheck() {
      if (aisStatus !== 'connected') return;
      const client = getClient();
      if (!client) return;

      const active = monitors.filter(m => m.active);
      for (const monitor of active) {
        try {
          const facility = CANADA_FACILITIES[monitor.location];
          if (!facility) continue;

          const dates = await client.getAvailableDates(facility.id);
          let found = 0;

          for (const d of dates.slice(0, 20)) {
            if (!d.date) continue;
            const times = await client.getAvailableTimes(facility.id, d.date);
            found += times.length;

            if (times.length > 0 && monitor.autoBook) {
              const result = await client.bookAppointment(facility.id, d.date, times[0]);
              addBooking({
                facilityId: facility.id,
                location: monitor.location,
                visaType: monitor.visaType,
                date: d.date,
                time: times[0],
                status: result.success ? 'booked' : 'failed',
                result,
                autoBooked: true,
              });
              if (result.success) {
                await sendLocalNotif(
                  'Appointment Booked!',
                  `${facility.name} — ${d.date} at ${times[0]}`
                );
                pushNotification(`AUTO-BOOKED: ${facility.name} on ${d.date} at ${times[0]}`);
                updateMonitor(monitor.id, { active: false, slotsFound: found, lastChecked: new Date().toISOString() });
                break;
              }
            }
          }

          updateMonitor(monitor.id, { slotsFound: found, lastChecked: new Date().toISOString() });

          if (found > 0 && !monitor.autoBook) {
            await sendLocalNotif(
              `${found} Slot${found > 1 ? 's' : ''} Available!`,
              `${facility.name} — ${monitor.visaType}`
            );
            pushNotification(`${found} slot(s) found at ${facility.name} for ${monitor.visaType}`);
          }
        } catch (_) { /* silently skip */ }
      }
    }

    interval = setInterval(runCheck, 5 * 60 * 1000); // every 5 minutes
    return () => clearInterval(interval);
  }, [monitors, aisStatus]);

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        tabBarIcon: ({ focused }) => <TabIcon label={route.name} focused={focused} />,
        tabBarActiveTintColor: colors.blue,
        tabBarInactiveTintColor: colors.gray500,
        tabBarStyle: { paddingBottom: 4 },
        headerStyle: { backgroundColor: colors.blue },
        headerTintColor: '#fff',
        headerTitleStyle: { fontWeight: '700' },
      })}
    >
      <Tab.Screen name="Home"     component={LoginScreen}    options={{ title: 'US Visa Bot 🇺🇸🇨🇦' }} />
      <Tab.Screen name="Slots"    component={SlotsScreen}    options={{ title: 'Check Slots' }} />
      <Tab.Screen name="Monitors" component={MonitorsScreen} options={{ title: 'Monitors' }} />
      <Tab.Screen name="Bookings" component={BookingsScreen} options={{ title: 'Bookings' }} />
    </Tab.Navigator>
  );
}

// Background fetch task (runs when app is backgrounded)
TaskManager.defineTask(BG_TASK, async () => {
  // Background tasks can't access React context directly.
  // We rely on the foreground loop for full functionality.
  // This is a minimal ping to keep the app alive on Android.
  return BackgroundFetch.BackgroundFetchResult.NewData;
});

async function registerBackgroundFetch() {
  try {
    await BackgroundFetch.registerTaskAsync(BG_TASK, {
      minimumInterval: 15 * 60, // 15 minutes minimum (OS-enforced)
      stopOnTerminate: false,
      startOnBoot: true,
    });
  } catch (_) { /* background fetch not always available */ }
}

export default function App() {
  useEffect(() => { registerBackgroundFetch(); }, []);

  return (
    <AppProvider>
      <NavigationContainer>
        <Navigator />
      </NavigationContainer>
    </AppProvider>
  );
}
