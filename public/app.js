/* eslint-disable no-undef */
(function () {
  let locations = [];
  let visaTypes = [];

  async function init() {
    await loadLocations();
    populateSelects();
    bindEvents();
    loadMonitors();
    loadHistory();
    setInterval(loadMonitors, 30000);
    setInterval(pollNotifications, 60000);
  }

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
    const locationOpts = locations.map(l => `<option value="${l.id}">${l.name}, ${l.country}</option>`).join('');
    const visaOpts = visaTypes.map(v => `<option value="${v.id}">${v.name}</option>`).join('');

    ['qc-location', 'm-location'].forEach(id => {
      document.getElementById(id).innerHTML = `<option value="">-- Select --</option>${locationOpts}`;
    });
    ['qc-visa', 'm-visa'].forEach(id => {
      document.getElementById(id).innerHTML = `<option value="">-- Select --</option>${visaOpts}`;
    });
  }

  function bindEvents() {
    document.getElementById('quick-check-form').addEventListener('submit', onQuickCheck);
    document.getElementById('add-monitor-form').addEventListener('submit', onAddMonitor);
    document.getElementById('notification-bell').addEventListener('click', toggleNotifPanel);
    document.getElementById('close-notif').addEventListener('click', () => {
      document.getElementById('notif-panel').classList.add('hidden');
    });
  }

  async function onQuickCheck(e) {
    e.preventDefault();
    const location = document.getElementById('qc-location').value;
    const visaType = document.getElementById('qc-visa').value;
    if (!location || !visaType) return;

    const resultsEl = document.getElementById('quick-results');
    resultsEl.classList.remove('hidden');
    resultsEl.innerHTML = '<p class="loading-msg"><span class="spinner"></span>Checking available slots...</p>';

    try {
      const res = await fetch(`/api/slots?location=${encodeURIComponent(location)}&visaType=${encodeURIComponent(visaType)}`);
      const slots = await res.json();
      renderSlots(slots, resultsEl);
    } catch (err) {
      resultsEl.innerHTML = `<p style="color:var(--red)">Error: ${err.message}</p>`;
    }
  }

  function renderSlots(slots, container) {
    const available = slots.filter(s => s.available);
    const total = slots.length;

    let html = `<div class="summary-bar">
      <span>Checked <strong>${total}</strong> upcoming working days</span>
      <span>Available: <strong>${available.length}</strong></span>
      <span>Unavailable: ${total - available.length}</span>
    </div>`;

    if (available.length === 0) {
      html += '<p class="empty-state" style="padding:16px 0">No open slots found in the next 30 days.</p>';
    } else {
      html += '<div class="slot-grid">';
      for (const s of slots) {
        if (!s.available) continue;
        html += `<div class="slot-card available">
          <div class="slot-date">${formatDate(s.date)}</div>
          <div class="slot-time">${s.time}</div>
          <a class="slot-link" href="${s.bookingUrl}" target="_blank" rel="noopener">Book now &rarr;</a>
        </div>`;
      }
      html += '</div>';
    }

    container.innerHTML = html;
  }

  async function onAddMonitor(e) {
    e.preventDefault();
    const location = document.getElementById('m-location').value;
    const visaType = document.getElementById('m-visa').value;
    const preferredDate = document.getElementById('m-date').value || null;
    const email = document.getElementById('m-email').value || null;
    if (!location || !visaType) return;

    try {
      const res = await fetch('/api/monitors', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ location, visaType, preferredDate, email }),
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
      const res = await fetch('/api/monitors');
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
        m.lastChecked ? `Last checked: ${timeAgo(m.lastChecked)}` : 'Not checked yet',
        m.slotsFound > 0 ? `${m.slotsFound} slot(s) found` : '',
      ].filter(Boolean).join(' &middot; ');

      return `<div class="monitor-item">
        <div class="monitor-info">
          <div class="title">
            <span class="status-dot ${m.active ? 'active' : 'paused'}"></span>
            ${locName} &mdash; ${m.visaType}
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

  async function loadHistory() {
    try {
      const res = await fetch('/api/history');
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
      const locName = (locations.find(l => l.id === h.location) || {}).name || h.location;
      const slotTags = h.slots.map(s => `<span class="h-slot-tag">${formatDate(s.date)} ${s.time}</span>`).join('');
      return `<div class="history-item">
        <div class="h-title">${locName} &mdash; ${h.visaType}</div>
        <div class="h-meta">${formatDate(h.checkedAt, true)}</div>
        <div class="h-slots">${slotTags}</div>
      </div>`;
    }).join('');
  }

  async function pollNotifications() {
    try {
      const res = await fetch('/api/notifications');
      const notifs = await res.json();
      if (notifs.length > 0) showNotifications(notifs);
    } catch (_) {}
  }

  function showNotifications(notifs) {
    const badge = document.getElementById('notif-badge');
    badge.textContent = notifs.length;
    badge.classList.remove('hidden');

    const list = document.getElementById('notif-list');
    list.innerHTML = notifs.map(n => `<div class="notif-item">
      <div class="n-msg">${n.message}</div>
      <div class="n-time">${timeAgo(n.createdAt)}</div>
    </div>`).join('') + list.innerHTML;
  }

  function toggleNotifPanel() {
    const panel = document.getElementById('notif-panel');
    panel.classList.toggle('hidden');
    document.getElementById('notif-badge').classList.add('hidden');
  }

  window.toggleMonitor = async function (id) {
    await fetch(`/api/monitors/${id}/toggle`, { method: 'PATCH' });
    loadMonitors();
  };

  window.deleteMonitor = async function (id) {
    if (!confirm('Remove this monitor?')) return;
    await fetch(`/api/monitors/${id}`, { method: 'DELETE' });
    loadMonitors();
  };

  function formatDate(iso, withTime = false) {
    if (!iso) return '';
    const d = new Date(iso);
    const opts = { month: 'short', day: 'numeric', year: 'numeric' };
    if (withTime) { opts.hour = '2-digit'; opts.minute = '2-digit'; }
    return d.toLocaleDateString('en-US', opts);
  }

  function timeAgo(iso) {
    const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
    if (diff < 60) return 'just now';
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
  }

  init();
})();
