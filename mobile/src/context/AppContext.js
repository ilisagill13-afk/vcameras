import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AISClient } from '../api/aisClient';

const AppContext = createContext(null);

export function AppProvider({ children }) {
  const [credentials, setCredentials] = useState(null);   // { email, password }
  const [aisStatus, setAisStatus]     = useState('disconnected'); // disconnected|connecting|connected|error
  const [aisError, setAisError]       = useState(null);
  const [appointmentId, setApptId]    = useState(null);
  const [monitors, setMonitors]       = useState([]);
  const [bookings, setBookings]       = useState([]);
  const [notifications, setNotifs]    = useState([]);
  const clientRef = useRef(null);

  // Restore saved credentials on app start
  useEffect(() => {
    AsyncStorage.getItem('ais_credentials').then(raw => {
      if (raw) {
        const creds = JSON.parse(raw);
        connectAIS(creds.email, creds.password, false);
      }
    });
    AsyncStorage.getItem('monitors').then(raw => {
      if (raw) setMonitors(JSON.parse(raw));
    });
    AsyncStorage.getItem('bookings').then(raw => {
      if (raw) setBookings(JSON.parse(raw));
    });
  }, []);

  async function connectAIS(email, password, save = true) {
    setAisStatus('connecting');
    setAisError(null);
    const client = new AISClient(email, password);
    try {
      const id = await client.login();
      clientRef.current = client;
      setCredentials({ email, password });
      setApptId(id);
      setAisStatus('connected');
      if (save) await AsyncStorage.setItem('ais_credentials', JSON.stringify({ email, password }));
      return true;
    } catch (err) {
      setAisStatus('error');
      setAisError(err.message);
      clientRef.current = null;
      return false;
    }
  }

  async function disconnectAIS() {
    clientRef.current = null;
    setCredentials(null);
    setAisStatus('disconnected');
    setAisError(null);
    setApptId(null);
    await AsyncStorage.removeItem('ais_credentials');
  }

  function getClient() { return clientRef.current; }

  function addMonitor(monitor) {
    const updated = [...monitors, { ...monitor, id: Date.now().toString(), active: true, createdAt: new Date().toISOString(), lastChecked: null, slotsFound: 0 }];
    setMonitors(updated);
    AsyncStorage.setItem('monitors', JSON.stringify(updated));
    return updated[updated.length - 1];
  }

  function removeMonitor(id) {
    const updated = monitors.filter(m => m.id !== id);
    setMonitors(updated);
    AsyncStorage.setItem('monitors', JSON.stringify(updated));
  }

  function toggleMonitor(id) {
    const updated = monitors.map(m => m.id === id ? { ...m, active: !m.active } : m);
    setMonitors(updated);
    AsyncStorage.setItem('monitors', JSON.stringify(updated));
  }

  function updateMonitor(id, patch) {
    const updated = monitors.map(m => m.id === id ? { ...m, ...patch } : m);
    setMonitors(updated);
    AsyncStorage.setItem('monitors', JSON.stringify(updated));
  }

  function addBooking(booking) {
    const updated = [...bookings, { ...booking, id: Date.now().toString(), createdAt: new Date().toISOString() }];
    setBookings(updated);
    AsyncStorage.setItem('bookings', JSON.stringify(updated));
    return updated[updated.length - 1];
  }

  function pushNotification(msg) {
    setNotifs(prev => [{ id: Date.now().toString(), msg, time: new Date().toISOString() }, ...prev.slice(0, 49)]);
  }

  return (
    <AppContext.Provider value={{
      credentials, aisStatus, aisError, appointmentId,
      monitors, bookings, notifications,
      connectAIS, disconnectAIS, getClient,
      addMonitor, removeMonitor, toggleMonitor, updateMonitor,
      addBooking, pushNotification,
    }}>
      {children}
    </AppContext.Provider>
  );
}

export function useApp() { return useContext(AppContext); }
