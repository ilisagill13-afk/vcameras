/* eslint-disable no-undef */
(function () {
  let locations = [];
  let visaTypes = [];
  let aisConnected = false;

  async function init() {
    await loadLocations();
    populateSelects();
    bindEvents();
    await checkAISStatus();
    loadMonitors();
    loadHistory();
    loadBookings();
    setInterval(loadMonitors, 30000);
    setInterval(pollNotifications, 60000);
    setInterval(checkAISStatus, 120000);
  }

  // ── Locations ───────────────────────────────────────────────────────────────

  async function loadLocations() {
    try {
      const res = await fetch('/api/locations');
      const data = await res.json();
      locations = data.locations;
      visaTypes = data.visaTypes;
    } catch (e) {
      console.error('Failed to load locations', e);
    }
  }

  function populateSelects() {
    const canadaLocs = locations.filter(l => l.country === 'Canada');
    const otherLocs  = locations.filter(l => l.country !== 'Canada');

    function buildOpts(locs) {
      if (canadaLocs.length && otherLocs.length) {
        return `<option value="">-- Select --</option>
          <optgroup label="Canada (AIS — Live)">${canadaLocs.map(l => `<option value="${l.id}">${l.name}, ${l.country}</option>`).join('')}</optgroup>
          <optgroup label="Other Countries (Mock)">${otherLocs.map(l => `<option value="${l.id}">${l.name}, ${l.country}</option>`).join('')}</optgroup>`;
      }
      return `<option value="">-- Select --</option>${locs.map(l => `<option value="${l.id}">${l.name}, ${l.country}</option>`).join('')}`;
    }

    const visaOpts = `<option value="">-- Select --</option>${visaTypes.map(v => `<option value="${v.id}">${v.name}</option>`).join('')}`;

    ['qc-location', 'm-location'].forEach(id => {
      document.getElementById(id).innerHTML = buildOpts(locations);
    });
    ['qc-visa', 'm-visa'].forEach(id => {
      document.getElementById(id).innerHTML = visaOpts;
    });
  }

  // ── AIS Credentials ─────────────────────────────────────────────────────────

  async function checkAISStatus() {
    try {
      const res = await fetch('/api/credentials/status');
      const data = await res.json();
      updateAISStatusUI(data);
    } catch (_) {}
  }

  function updateAISStatusUI(data) {
    const pill   = document.getElementById('ais-status-pill');
    const label  = document.getElementById('ais-status-label');
    const form   = document.getElementById('credentials-form');
    const conn   = document.getElementById('credentials-connected');
    const emailL = document.getElementById('cred-email-label');
    const apptL  = document.getElementById('cred-appt-label');

    aisConnected = data.status === 'connected';

    pill.className = `status-pill ${data.status}`;
    label.textContent = {
      connected:    'AIS Connected',
      connecting:   'Connecting...',
      error:        'Connection Error',
      disconnected: 'Not Connected',
    }[data.status] || data.status;

    if (data.status === 'connected') {
      form.classList.add('hidden');
      conn.classList.remove('hidden');
      emailL.textContent = data.email;
      apptL.textContent  = data.appointmentId ? `App ID: ${data.appointmentId}` : '';
    } else {
      form.classList.remove('hidden');
      conn.classList.add('hidden');
    }

    const errEl = document.getElementById('cred-error');
    if (data.error) {
      errEl.textContent = data.error;
      errEl.classList.remove('hidden');
    } else {
      errEl.classList.add('hidden');
    }
  }

  async function onConnectAIS(e) {
    e.preventDefault();
    const email    = document.getElementById('ais-email').value.trim();
    const password = document.getElementById('ais-password').value;
    if (!email || !password) return;

    const btn = document.getElementById('connect-btn');
    btn.disabled = true;
    btn.textContent = 'Connecting...';

    try {
      const res = await fetch('/api/credentials', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Connection failed');
      updateAISStatusUI({ status: 'connected', email, appointmentId: data.appointmentId });
    } catch (err) {
      updateAISStatusUI({ status: 'error', error: err.message });
    } finally {
      btn.disabled = false;
      btn.textContent = 'Connect to AIS Portal';
    }
  }

  async function onDisconnect() {
    await fetch('/api/credentials', { method: 'DELETE' });
    updateAISStatusUI({ status: 'disconnected' });
  }

  // ── Quick check ─────────────────────────────────────────────────────────────

  async function onQuickCheck(e) {
    e.preventDefault();
    const location = document.getElementById('qc-location').value;
    const visaType = document.getElementById('qc-visa').value;
    if (!location || !visaType) return;

    const resultsEl = document.getElementById('quick-results');
    resultsEl.classList.remove('hidden');
    resultsEl.innerHTML = '<p class="loading-msg"><span class="spinner"></span>Checking available slots...</p>';

    try {
      const res  = await fetch(`/api/slots?location=${encodeURIComponent(location)}&visaType=${encodeURIComponent(visaType)}`);
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Check failed');
      renderSlots(data.slots || data, resultsEl, data.live);
    } catch (err) {
      resultsEl.innerHTML = `<p style="color:var(--red)">Error: ${escHtml(err.message)}</p>`;
    }
  }

  function renderSlots(slots, container, live = false) {
    const available = slots.filter(s => s.available);

    let html = `<div class="summary-bar">
      <span>Checked <strong>${slots.length}</strong> dates</span>
      <span>Available: <strong>${available.length}</strong></span>
      <span class="badge ${live ? 'badge-green' : 'badge-gray'}">${live ? 'Live AIS Data' : 'Mock Data'}</span>
    </div>`;

    if (available.length === 0) {
      html += '<p class="empty-state" style="padding:16px 0">No open slots found.</p>';
    } else {
      html += '<div class="slot-grid">';
      for (const s of available) {
        const bookBtn = s.facilityId && aisConnected
          ? `<button class="btn btn-book" onclick="bookSlot(${s.facilityId},'${s.date}','${s.time}','${escHtml(s.location || '')}','${escHtml(s.visaType || '')}',this)">Book Now</button>`
          : `<a class="slot-link" href="${escHtml(s.bookingUrl)}" target="_blank" rel="noopener">Book on AIS &rarr;</a>`;
        html += `<div class="slot-card available">
          <div class="slot-date">${formatDate(s.date)}</div>
          <div class="slot-time">${s.time}</div>
          ${bookBtn}
        </div>`;
      }
      html += '</div>';
    }

    container.innerHTML = html;
  }

  // ── Add Monitor ─────────────────────────────────────────────────────────────

  async function onAddMonitor(e) {
    e.preventDefault();
    const location      = document.getElementById('m-location').value;
    const visaType      = document.getElementById('m-visa').value;
    const preferredDate = document.getElementById('m-date').value || null;
    const email         = document.getElementById('m-email').value || null;
    const autoBook      = document.getElementById('m-autobook').checked;
    if (!location || !visaType) return;

    try {
      const res = await fetch('/api/monitors', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ location, visaType, preferredDate, email, autoBook }),
      });
      if (!res.ok) throw new Error('Failed to add monitor');
      e.target.reset();
      await loadMonitors();
    } catch (err) {
      alert('Error adding monitor: ' + err.message);
    }
  }

  async function loadMonitors() {
    try {
      const res      = await fetch('/api/monitors');
      const monitors = await res.json();
      renderMonitors(monitors);
    } catch (_) {}
  }

  function renderMonitors(monitors) {
    const el = document.getElementById('monitors-list');
    if (monitors.length === 0) {
      el.innerHTML = '<p class="empty-state">No active monitors. Add one above.</p>';
      return;
    }
    el.innerHTML = monitors.map(m => {
      const locName = (locations.find(l => l.id === m.location) || {}).name || m.location;
      const meta = [
        m.preferredDate ? `Preferred: ${formatDate(m.preferredDate)}` : '',
        m.lastChecked   ? `Last checked: ${timeAgo(m.lastChecked)}` : 'Not checked yet',
        m.slotsFound > 0 ? `${m.slotsFound} slot(s) found` : '',
        m.autoBook ? '<span class="badge badge-blue">Auto-Book ON</span>' : '',
      ].filter(Boolean).join(' &middot; ');

      return `<div class="monitor-item">
        <div class="monitor-info">
          <div class="title">
            <span class="status-dot ${m.active ? 'active' : 'paused'}"></span>
            ${escHtml(locName)} &mdash; ${escHtml(m.visaType)}
          </div>
          <div class="meta">${meta}</div>
        </div>
        <div class="monitor-actions">
          <button class="btn btn-ghost" onclick="toggleMonitor('${m.id}')">${m.active ? 'Pause' : 'Resume'}</button>
          <button class="btn btn-danger" onclick="deleteMonitor('${m.id}')">Remove</button>
        </div>
      </div>`;
    }).join('');
  }

  // ── Bookings ─────────────────────────────────────────────────────────────────

  async function loadBookings() {
    try {
      const res  = await fetch('/api/bookings');
      const data = await res.json();
      renderBookings(data);
    } catch (_) {}
  }

  function renderBookings(bookings) {
    const el = document.getElementById('bookings-list');
    if (bookings.length === 0) {
      el.innerHTML = '<p class="empty-state">No booking attempts yet.</p>';
      return;
    }
    el.innerHTML = [...bookings].reverse().map(b => {
      const locName = (locations.find(l => l.id === b.location) || {}).name || b.location || 'Unknown';
      const statusClass = b.status === 'booked' ? 'badge-green' : b.status === 'pending' ? 'badge-gray' : 'badge-red';
      return `<div class="history-item">
        <div class="h-title">
          ${escHtml(locName)} &mdash; ${escHtml(b.visaType || '')}
          ${b.autoBooked ? '<span class="badge badge-blue" style="margin-left:6px">Auto</span>' : ''}
          <span class="badge ${statusClass}" style="margin-left:6px">${b.status}</span>
        </div>
        <div class="h-meta">${formatDate(b.createdAt, true)}</div>
        <div class="h-slots">
          <span class="h-slot-tag">${formatDate(b.date)} ${b.time}</span>
          ${b.result && b.result.confirmationUrl
            ? `<a href="${escHtml(b.result.confirmationUrl)}" target="_blank" rel="noopener" class="slot-link">View on AIS &rarr;</a>`
            : ''}
        </div>
      </div>`;
    }).join('');
  }

  // ── History ──────────────────────────────────────────────────────────────────

  async function loadHistory() {
    try {
      const res     = await fetch('/api/history');
      const history = await res.json();
      renderHistory(history);
    } catch (_) {}
  }

  function renderHistory(history) {
    const el = document.getElementById('history-list');
    if (history.length === 0) {
      el.innerHTML = '<p class="empty-state">No slots recorded yet. Start a monitor to track availability.</p>';
      return;
    }
    el.innerHTML = [...history].reverse().map(h => {
      const locName   = (locations.find(l => l.id === h.location) || {}).name || h.location;
      const slotTags  = h.slots.map(s => `<span class="h-slot-tag">${formatDate(s.date)} ${s.time}</span>`).join('');
      return `<div class="history-item">
        <div class="h-title">${escHtml(locName)} &mdash; ${escHtml(h.visaType)}</div>
        <div class="h-meta">${formatDate(h.checkedAt, true)}</div>
        <div class="h-slots">${slotTags}</div>
      </div>`;
    }).join('');
  }

  // ── Notifications ─────────────────────────────────────────────────────────────

  async function pollNotifications() {
    try {
      const res   = await fetch('/api/notifications');
      const notifs = await res.json();
      if (notifs.length > 0) showNotifications(notifs);
    } catch (_) {}
  }

  function showNotifications(notifs) {
    const badge = document.getElementById('notif-badge');
    badge.textContent = notifs.length;
    badge.classList.remove('hidden');

    const list = document.getElementById('notif-list');
    const newItems = notifs.map(n => `<div class="notif-item ${n.type === 'booking' ? 'notif-booking' : ''}">
      <div class="n-msg">${escHtml(n.message)}</div>
      <div class="n-time">${timeAgo(n.createdAt)}</div>
    </div>`).join('');
    list.innerHTML = newItems + list.innerHTML;

    // Refresh bookings if a booking notification arrived
    if (notifs.some(n => n.type === 'booking')) loadBookings();
  }

  function toggleNotifPanel() {
    const panel = document.getElementById('notif-panel');
    panel.classList.toggle('hidden');
    document.getElementById('notif-badge').classList.add('hidden');
  }

  // ── Bind all events ───────────────────────────────────────────────────────────

  function bindEvents() {
    document.getElementById('quick-check-form').addEventListener('submit', onQuickCheck);
    document.getElementById('add-monitor-form').addEventListener('submit', onAddMonitor);
    document.getElementById('credentials-form').addEventListener('submit', onConnectAIS);
    document.getElementById('disconnect-btn').addEventListener('click', onDisconnect);
    document.getElementById('notification-bell').addEventListener('click', toggleNotifPanel);
    document.getElementById('close-notif').addEventListener('click', () => {
      document.getElementById('notif-panel').classList.add('hidden');
    });
  }

  // ── Global actions (called from inline onclick) ────────────────────────────

  window.toggleMonitor = async function (id) {
    await fetch(`/api/monitors/${id}/toggle`, { method: 'PATCH' });
    loadMonitors();
  };

  window.deleteMonitor = async function (id) {
    if (!confirm('Remove this monitor?')) return;
    await fetch(`/api/monitors/${id}`, { method: 'DELETE' });
    loadMonitors();
  };

  window.bookSlot = async function (facilityId, date, time, location, visaType, btn) {
    if (!confirm(`Book appointment at ${location} on ${date} at ${time}?`)) return;
    btn.disabled = true;
    btn.textContent = 'Booking...';
    try {
      const res  = await fetch('/api/book', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ facilityId, date, time, location, visaType }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Booking failed');

      if (data.status === 'booked') {
        btn.textContent = 'Booked!';
        btn.className   = 'btn badge-green';
        loadBookings();
      } else {
        throw new Error('Booking was not confirmed by AIS');
      }
    } catch (err) {
      btn.disabled    = false;
      btn.textContent = 'Book Now';
      alert('Booking error: ' + err.message);
    }
  };

  // ── Helpers ───────────────────────────────────────────────────────────────────

  function formatDate(iso, withTime = false) {
    if (!iso) return '';
    const d    = new Date(iso);
    const opts = { month: 'short', day: 'numeric', year: 'numeric' };
    if (withTime) { opts.hour = '2-digit'; opts.minute = '2-digit'; }
    return d.toLocaleDateString('en-US', opts);
  }

  function timeAgo(iso) {
    const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
    if (diff < 60) return 'just now';
    if (diff < 3600)  return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
  }

  function escHtml(str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  init();
})();
