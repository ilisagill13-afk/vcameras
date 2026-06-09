const express = require('express');
const path = require('path');
const cron = require('node-cron');
const { checkSlots, getLocations } = require('./checker');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// In-memory state
const state = {
  monitors: [],
  slotHistory: [],
  notifications: [],
};

app.get('/api/locations', (req, res) => {
  res.json(getLocations());
});

app.get('/api/monitors', (req, res) => {
  res.json(state.monitors);
});

app.post('/api/monitors', (req, res) => {
  const { location, visaType, preferredDate, email } = req.body;
  if (!location || !visaType) {
    return res.status(400).json({ error: 'location and visaType are required' });
  }
  const monitor = {
    id: Date.now().toString(),
    location,
    visaType,
    preferredDate: preferredDate || null,
    email: email || null,
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

app.get('/api/slots', async (req, res) => {
  const { location, visaType } = req.query;
  if (!location || !visaType) {
    return res.status(400).json({ error: 'location and visaType are required' });
  }
  try {
    const slots = await checkSlots(location, visaType);
    res.json(slots);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.get('/api/history', (req, res) => {
  res.json(state.slotHistory.slice(-100));
});

app.get('/api/notifications', (req, res) => {
  const unseen = state.notifications.filter(n => !n.seen);
  unseen.forEach(n => (n.seen = true));
  res.json(unseen);
});

// Background check every 5 minutes for active monitors
cron.schedule('*/5 * * * *', async () => {
  const active = state.monitors.filter(m => m.active);
  for (const monitor of active) {
    try {
      const slots = await checkSlots(monitor.location, monitor.visaType);
      monitor.lastChecked = new Date().toISOString();
      const available = slots.filter(s => s.available);
      if (available.length > 0) {
        monitor.slotsFound = available.length;
        state.slotHistory.push({
          monitorId: monitor.id,
          location: monitor.location,
          visaType: monitor.visaType,
          slots: available,
          checkedAt: new Date().toISOString(),
        });
        state.notifications.push({
          id: Date.now().toString(),
          message: `${available.length} slot(s) found at ${monitor.location} for ${monitor.visaType}`,
          location: monitor.location,
          visaType: monitor.visaType,
          slots: available,
          seen: false,
          createdAt: new Date().toISOString(),
        });
      }
    } catch (_) {
      // silently skip failed checks
    }
  }
});

app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`US Visa Slot Checker running on http://localhost:${PORT}`);
});
