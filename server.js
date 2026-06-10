const express = require('express');
const path = require('path');
const cron = require('node-cron');
const { checkSlots, findEarliestSlot, getLocations, LOCATIONS } = require('./checker');
const { AISClient, CANADA_FACILITIES } = require('./ais-checker');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// In-memory state
const state = {
  monitors: [],
  slotHistory: [],
  notifications: [],
  bookings: [],
  aisCredentials: null,    // { email, password }
  aisClient: null,         // AISClient instance
  aisStatus: 'disconnected', // 'disconnected' | 'connecting' | 'connected' | 'error'
  aisError: null,
};

// ── Credentials ──────────────────────────────────────────────────────────────

app.post('/api/credentials', async (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ error: 'email and password are required' });
  }

  state.aisCredentials = { email, password };
  state.aisClient = new AISClient(email, password);
  state.aisStatus = 'connecting';
  state.aisError = null;

  try {
    await state.aisClient.login();
    state.aisStatus = 'connected';
    res.json({
      status: 'connected',
      appointmentId: state.aisClient.appointmentId,
      message: 'Successfully connected to AIS portal',
    });
  } catch (err) {
    state.aisStatus = 'error';
    state.aisError = err.message;
    state.aisClient = null;
    res.status(401).json({ error: err.message });
  }
});

app.get('/api/credentials/status', (req, res) => {
  res.json({
    status: state.aisStatus,
    email: state.aisCredentials ? state.aisCredentials.email : null,
    appointmentId: state.aisClient ? state.aisClient.appointmentId : null,
    error: state.aisError,
  });
});

app.delete('/api/credentials', (req, res) => {
  state.aisCredentials = null;
  state.aisClient = null;
  state.aisStatus = 'disconnected';
  state.aisError = null;
  res.json({ success: true });
});

// ── Locations ─────────────────────────────────────────────────────────────────

app.get('/api/locations', (req, res) => {
  res.json(getLocations());
});

// ── Slot check ────────────────────────────────────────────────────────────────

app.get('/api/slots', async (req, res) => {
  const { location, visaType } = req.query;
  if (!location || !visaType) {
    return res.status(400).json({ error: 'location and visaType are required' });
  }
  try {
    const slots = await checkSlots(location, visaType, state.aisClient);
    res.json({ slots, live: !!state.aisClient });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ── Find slots across consulates ──────────────────────────────────────────────

app.get('/api/find-slots', async (req, res) => {
  const { visaType, country } = req.query;
  if (!visaType) {
    return res.status(400).json({ error: 'visaType is required' });
  }

  const targets = LOCATIONS.filter(l => !country || l.country === country);
  if (targets.length === 0) {
    return res.status(400).json({ error: `No locations found for country: ${country}` });
  }

  const settled = await Promise.allSettled(
    targets.map(l => findEarliestSlot(l.id, visaType, state.aisClient))
  );

  const results = settled.map((r, i) =>
    r.status === 'fulfilled'
      ? r.value
      : { location: targets[i], slot: null, openDates: 0, live: false, error: r.reason.message }
  );

  // Locations with an open slot first, sorted by earliest date
  results.sort((a, b) => {
    if (a.slot && b.slot) return a.slot.date.localeCompare(b.slot.date);
    if (a.slot) return -1;
    if (b.slot) return 1;
    return a.location.name.localeCompare(b.location.name);
  });

  res.json({ results, visaType, checkedAt: new Date().toISOString() });
});

// ── Booking ───────────────────────────────────────────────────────────────────

app.post('/api/book', async (req, res) => {
  const { facilityId, date, time, location, visaType } = req.body;
  if (!facilityId || !date || !time) {
    return res.status(400).json({ error: 'facilityId, date, and time are required' });
  }
  if (!state.aisClient) {
    return res.status(401).json({ error: 'AIS credentials not set. Connect your AIS account first.' });
  }

  const booking = {
    id: Date.now().toString(),
    facilityId,
    date,
    time,
    location,
    visaType,
    status: 'pending',
    createdAt: new Date().toISOString(),
    result: null,
  };
  state.bookings.push(booking);

  try {
    const result = await state.aisClient.bookAppointment(facilityId, date, time);
    booking.status = result.success ? 'booked' : 'failed';
    booking.result = result;

    if (result.success) {
      state.notifications.push({
        id: Date.now().toString(),
        message: `Appointment BOOKED: ${location} on ${date} at ${time}`,
        type: 'booking',
        seen: false,
        createdAt: new Date().toISOString(),
      });
    }

    res.json(booking);
  } catch (err) {
    booking.status = 'error';
    booking.result = { error: err.message };
    res.status(500).json({ error: err.message, booking });
  }
});

app.get('/api/bookings', (req, res) => {
  res.json(state.bookings.slice(-50));
});

// ── Monitors ──────────────────────────────────────────────────────────────────

app.get('/api/monitors', (req, res) => {
  res.json(state.monitors);
});

app.post('/api/monitors', (req, res) => {
  const { location, visaType, preferredDate, email, autoBook } = req.body;
  if (!location || !visaType) {
    return res.status(400).json({ error: 'location and visaType are required' });
  }
  const monitor = {
    id: Date.now().toString(),
    location,
    visaType,
    preferredDate: preferredDate || null,
    email: email || null,
    autoBook: !!autoBook,
    active: true,
    createdAt: new Date().toISOString(),
    lastChecked: null,
    slotsFound: 0,
  };
  state.monitors.push(monitor);
  res.status(201).json(monitor);
});

app.delete('/api/monitors/:id', (req, res) => {
  const idx = state.monitors.findIndex(m => m.id === req.params.id);
  if (idx === -1) return res.status(404).json({ error: 'Monitor not found' });
  state.monitors.splice(idx, 1);
  res.json({ success: true });
});

app.patch('/api/monitors/:id/toggle', (req, res) => {
  const monitor = state.monitors.find(m => m.id === req.params.id);
  if (!monitor) return res.status(404).json({ error: 'Monitor not found' });
  monitor.active = !monitor.active;
  res.json(monitor);
});

// ── History & notifications ────────────────────────────────────────────────────

app.get('/api/history', (req, res) => {
  res.json(state.slotHistory.slice(-100));
});

app.get('/api/notifications', (req, res) => {
  const unseen = state.notifications.filter(n => !n.seen);
  unseen.forEach(n => (n.seen = true));
  res.json(unseen);
});

// ── Background cron (every 5 minutes) ─────────────────────────────────────────

cron.schedule('*/5 * * * *', async () => {
  const active = state.monitors.filter(m => m.active);
  for (const monitor of active) {
    try {
      const slots = await checkSlots(monitor.location, monitor.visaType, state.aisClient);
      monitor.lastChecked = new Date().toISOString();

      const available = slots.filter(s => s.available);
      if (available.length === 0) continue;

      monitor.slotsFound = available.length;
      state.slotHistory.push({
        monitorId: monitor.id,
        location: monitor.location,
        visaType: monitor.visaType,
        slots: available,
        checkedAt: new Date().toISOString(),
      });

      // Auto-book: pick the first available slot when enabled
      if (monitor.autoBook && state.aisClient && available[0].facilityId) {
        const slot = available[0];
        try {
          const result = await state.aisClient.bookAppointment(slot.facilityId, slot.date, slot.time);
          const booking = {
            id: Date.now().toString(),
            facilityId: slot.facilityId,
            date: slot.date,
            time: slot.time,
            location: monitor.location,
            visaType: monitor.visaType,
            status: result.success ? 'booked' : 'failed',
            result,
            createdAt: new Date().toISOString(),
            autoBooked: true,
          };
          state.bookings.push(booking);

          if (result.success) {
            monitor.active = false; // stop monitoring after successful booking
            state.notifications.push({
              id: Date.now().toString(),
              message: `AUTO-BOOKED: ${slot.location} on ${slot.date} at ${slot.time} for ${monitor.visaType}`,
              type: 'booking',
              seen: false,
              createdAt: new Date().toISOString(),
            });
            continue;
          }
        } catch (_) {
          // booking error — still notify about available slot
        }
      }

      state.notifications.push({
        id: Date.now().toString(),
        message: `${available.length} slot(s) found at ${monitor.location} for ${monitor.visaType}`,
        type: 'slot',
        location: monitor.location,
        visaType: monitor.visaType,
        slots: available,
        seen: false,
        createdAt: new Date().toISOString(),
      });
    } catch (_) {
      // silently skip failed checks
    }
  }
});

app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`US Visa Slot Bot (Canada) running on http://localhost:${PORT}`);
});
