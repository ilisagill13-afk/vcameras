import axios from 'axios';

const AIS_BASE = 'https://ais.usvisa-info.com/en-ca/niv';
const UA = 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

export const CANADA_FACILITIES = {
  calgary:      { id: 89,  name: 'Calgary' },
  halifax:      { id: 90,  name: 'Halifax' },
  montreal:     { id: 91,  name: 'Montreal' },
  ottawa:       { id: 92,  name: 'Ottawa' },
  'quebec-city':{ id: 93,  name: 'Quebec City' },
  toronto:      { id: 94,  name: 'Toronto' },
  vancouver:    { id: 95,  name: 'Vancouver' },
};

export const VISA_TYPES = [
  { id: 'B1/B2', label: 'B1/B2 — Business/Tourism' },
  { id: 'F1',    label: 'F1 — Student' },
  { id: 'J1',    label: 'J1 — Exchange Visitor' },
  { id: 'H1B',   label: 'H1B — Specialty Occupation' },
  { id: 'L1',    label: 'L1 — Intracompany Transfer' },
  { id: 'O1',    label: 'O1 — Extraordinary Ability' },
];

// Parse Set-Cookie headers into a plain object
function parseCookies(raw) {
  if (!raw) return {};
  const list = Array.isArray(raw) ? raw : [raw];
  const out = {};
  for (const h of list) {
    const [pair] = h.split(';');
    const eq = pair.indexOf('=');
    if (eq > 0) out[pair.slice(0, eq).trim()] = pair.slice(eq + 1).trim();
  }
  return out;
}

function cookieStr(obj) {
  return Object.entries(obj).map(([k, v]) => `${k}=${v}`).join('; ');
}

function extract(html, pattern) {
  const m = html.match(pattern);
  return m ? m[1] : null;
}

export class AISClient {
  constructor(email, password) {
    this.email = email;
    this.password = password;
    this.jar = {};
    this.csrfToken = null;
    this.appointmentId = null;
    this.loggedIn = false;
    this.lastActivity = null;
  }

  _merge(headers) {
    Object.assign(this.jar, parseCookies(headers['set-cookie']));
  }

  _headers(extra = {}) {
    return { 'User-Agent': UA, 'Cookie': cookieStr(this.jar), ...extra };
  }

  async login() {
    // 1. Load sign-in page — get CSRF + session cookie
    const page = await axios.get(`${AIS_BASE}/users/sign_in`, {
      headers: { 'User-Agent': UA },
      maxRedirects: 5,
    });
    this._merge(page.headers);
    this.csrfToken = extract(page.data, /name="authenticity_token"[^>]+value="([^"]+)"/);
    if (!this.csrfToken) throw new Error('CSRF token not found. AIS site may have changed.');

    // 2. POST credentials
    const body = new URLSearchParams({
      'utf8': '✓',
      'user[email]': this.email,
      'user[password]': this.password,
      'policy_confirmed': '1',
      'commit': 'Sign In',
    }).toString();

    const res = await axios.post(`${AIS_BASE}/users/sign_in`, body, {
      headers: this._headers({
        'Content-Type': 'application/x-www-form-urlencoded',
        'Referer': `${AIS_BASE}/users/sign_in`,
        'Origin': 'https://ais.usvisa-info.com',
        'Accept': 'text/html,application/xhtml+xml;q=0.9,*/*;q=0.8',
      }),
      maxRedirects: 10,
      validateStatus: s => s < 500,
    });
    this._merge(res.headers);

    const finalUrl = res.request?.responseURL || res.config?.url || '';
    let id = extract(finalUrl, /\/schedule\/(\d+)/) || extract(res.data, /\/en-ca\/niv\/schedule\/(\d+)/);
    if (!id) throw new Error('Login failed or no active visa application. Check your AIS credentials.');

    this.appointmentId = id;
    const meta = extract(res.data, /<meta[^>]+name="csrf-token"[^>]+content="([^"]+)"/);
    if (meta) this.csrfToken = meta;

    this.loggedIn = true;
    this.lastActivity = Date.now();
    return id;
  }

  async _ensureSession() {
    const TTL = 20 * 60 * 1000;
    if (!this.loggedIn || !this.lastActivity || Date.now() - this.lastActivity > TTL) {
      await this.login();
    }
  }

  async getAvailableDates(facilityId) {
    await this._ensureSession();
    const url = `${AIS_BASE}/schedule/${this.appointmentId}/appointment/days/${facilityId}.json?appointments[expedite]=false`;
    const res = await axios.get(url, {
      headers: this._headers({
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': `${AIS_BASE}/schedule/${this.appointmentId}/appointment`,
      }),
      validateStatus: s => s < 500,
    });
    this._merge(res.headers);
    this.lastActivity = Date.now();

    if (res.status === 401 || res.status === 403) {
      this.loggedIn = false;
      await this.login();
      return this.getAvailableDates(facilityId);
    }
    return Array.isArray(res.data) ? res.data : [];
  }

  async getAvailableTimes(facilityId, date) {
    await this._ensureSession();
    const url = `${AIS_BASE}/schedule/${this.appointmentId}/appointment/times/${facilityId}.json?date=${date}&appointments[expedite]=false`;
    const res = await axios.get(url, {
      headers: this._headers({
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': `${AIS_BASE}/schedule/${this.appointmentId}/appointment`,
      }),
      validateStatus: s => s < 500,
    });
    this._merge(res.headers);
    this.lastActivity = Date.now();
    return res.data?.available_times || [];
  }

  async bookAppointment(facilityId, date, time) {
    await this._ensureSession();

    // Refresh CSRF before booking
    const apptPage = await axios.get(`${AIS_BASE}/schedule/${this.appointmentId}/appointment`, {
      headers: this._headers({ 'Accept': 'text/html,application/xhtml+xml;q=0.9,*/*;q=0.8' }),
      validateStatus: s => s < 500,
    });
    this._merge(apptPage.headers);
    const fresh = extract(apptPage.data, /name="authenticity_token"[^>]+value="([^"]+)"/);
    if (fresh) this.csrfToken = fresh;

    const body = new URLSearchParams({
      'utf8': '✓',
      'authenticity_token': this.csrfToken,
      'confirmed_limit_message': '1',
      'use_consulate_appointment_capacity': 'true',
      'appointments[consulate_appointment][facility_id]': String(facilityId),
      'appointments[consulate_appointment][date]': date,
      'appointments[consulate_appointment][time]': time,
    }).toString();

    const res = await axios.post(
      `${AIS_BASE}/schedule/${this.appointmentId}/appointment`,
      body,
      {
        headers: this._headers({
          'Content-Type': 'application/x-www-form-urlencoded',
          'Referer': `${AIS_BASE}/schedule/${this.appointmentId}/appointment`,
          'Origin': 'https://ais.usvisa-info.com',
          'Accept': 'text/html,application/xhtml+xml;q=0.9,*/*;q=0.8',
        }),
        maxRedirects: 5,
        validateStatus: s => s < 500,
      }
    );
    this._merge(res.headers);
    this.lastActivity = Date.now();

    const finalUrl = res.request?.responseURL || '';
    const success = (res.status === 200 || res.status === 302)
      && (finalUrl.includes('/appointment') || (res.data && res.data.includes('Your appointment')));

    return {
      success,
      status: res.status,
      confirmationUrl: `${AIS_BASE}/schedule/${this.appointmentId}/appointment`,
    };
  }
}
