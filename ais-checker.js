const axios = require('axios');

const AIS_BASE = 'https://ais.usvisa-info.com/en-ca/niv';

// AIS facility IDs for Canadian consulates
const CANADA_FACILITIES = {
  'calgary':      { id: 89,  name: 'Calgary' },
  'halifax':      { id: 90,  name: 'Halifax' },
  'montreal':     { id: 91,  name: 'Montreal' },
  'ottawa':       { id: 92,  name: 'Ottawa' },
  'quebec-city':  { id: 93,  name: 'Quebec City' },
  'toronto':      { id: 94,  name: 'Toronto' },
  'vancouver':    { id: 95,  name: 'Vancouver' },
};

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

function parseCookieHeaders(headers) {
  const raw = headers['set-cookie'];
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

function cookieString(obj) {
  return Object.entries(obj).map(([k, v]) => `${k}=${v}`).join('; ');
}

function extractFirstMatch(html, pattern) {
  const m = html.match(pattern);
  return m ? m[1] : null;
}

class AISClient {
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
    Object.assign(this.jar, parseCookieHeaders(headers));
  }

  _baseHeaders(extra = {}) {
    return {
      'User-Agent': UA,
      'Accept-Language': 'en-US,en;q=0.9',
      'Cookie': cookieString(this.jar),
      ...extra,
    };
  }

  async login() {
    // Step 1: load sign-in page to get CSRF + session cookie
    const page = await axios.get(`${AIS_BASE}/users/sign_in`, {
      headers: { 'User-Agent': UA },
      maxRedirects: 5,
    });
    this._merge(page.headers);
    this.csrfToken = extractFirstMatch(
      page.data,
      /name="authenticity_token"[^>]+value="([^"]+)"/
    );
    if (!this.csrfToken) throw new Error('Cannot find CSRF token on sign-in page');

    // Step 2: POST credentials
    const body = new URLSearchParams({
      'utf8': '✓',
      'user[email]': this.email,
      'user[password]': this.password,
      'policy_confirmed': '1',
      'commit': 'Sign In',
    });

    const loginRes = await axios.post(`${AIS_BASE}/users/sign_in`, body.toString(), {
      headers: this._baseHeaders({
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-CSRF-Token': this.csrfToken,
        'Referer': `${AIS_BASE}/users/sign_in`,
        'Origin': 'https://ais.usvisa-info.com',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      }),
      maxRedirects: 10,
      validateStatus: s => s < 500,
    });

    this._merge(loginRes.headers);

    // Extract appointment/schedule ID from redirected URL or page body
    const finalUrl = loginRes.request?.res?.responseUrl || '';
    let scheduleId = extractFirstMatch(finalUrl, /\/schedule\/(\d+)/);
    if (!scheduleId) {
      scheduleId = extractFirstMatch(loginRes.data, /\/en-ca\/niv\/schedule\/(\d+)/);
    }
    if (!scheduleId) {
      throw new Error('Login failed or no active visa application found. Check your AIS credentials.');
    }

    this.appointmentId = scheduleId;

    // Refresh CSRF from meta tag on the loaded page
    const metaCsrf = extractFirstMatch(loginRes.data, /<meta[^>]+name="csrf-token"[^>]+content="([^"]+)"/);
    if (metaCsrf) this.csrfToken = metaCsrf;

    this.loggedIn = true;
    this.lastActivity = Date.now();
    return scheduleId;
  }

  async _ensureSession() {
    const SESSION_TTL = 20 * 60 * 1000; // 20 minutes
    if (!this.loggedIn || !this.lastActivity || Date.now() - this.lastActivity > SESSION_TTL) {
      await this.login();
    }
  }

  async getAvailableDates(facilityId) {
    await this._ensureSession();

    const url = `${AIS_BASE}/schedule/${this.appointmentId}/appointment/days/${facilityId}.json?appointments[expedite]=false`;
    const res = await axios.get(url, {
      headers: this._baseHeaders({
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
      headers: this._baseHeaders({
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': `${AIS_BASE}/schedule/${this.appointmentId}/appointment`,
      }),
      validateStatus: s => s < 500,
    });
    this._merge(res.headers);
    this.lastActivity = Date.now();

    return (res.data && Array.isArray(res.data.available_times)) ? res.data.available_times : [];
  }

  async bookAppointment(facilityId, date, time) {
    await this._ensureSession();

    // Refresh CSRF from appointment page before booking
    const apptPage = await axios.get(`${AIS_BASE}/schedule/${this.appointmentId}/appointment`, {
      headers: this._baseHeaders({
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Referer': `${AIS_BASE}/schedule/${this.appointmentId}/appointment`,
      }),
      validateStatus: s => s < 500,
    });
    this._merge(apptPage.headers);
    const freshCsrf = extractFirstMatch(apptPage.data, /name="authenticity_token"[^>]+value="([^"]+)"/);
    if (freshCsrf) this.csrfToken = freshCsrf;

    const body = new URLSearchParams({
      'utf8': '✓',
      'authenticity_token': this.csrfToken,
      'confirmed_limit_message': '1',
      'use_consulate_appointment_capacity': 'true',
      'appointments[consulate_appointment][facility_id]': String(facilityId),
      'appointments[consulate_appointment][date]': date,
      'appointments[consulate_appointment][time]': time,
    });

    const res = await axios.post(
      `${AIS_BASE}/schedule/${this.appointmentId}/appointment`,
      body.toString(),
      {
        headers: this._baseHeaders({
          'Content-Type': 'application/x-www-form-urlencoded',
          'X-CSRF-Token': this.csrfToken,
          'Referer': `${AIS_BASE}/schedule/${this.appointmentId}/appointment`,
          'Origin': 'https://ais.usvisa-info.com',
          'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        }),
        maxRedirects: 5,
        validateStatus: s => s < 500,
      }
    );

    this._merge(res.headers);
    this.lastActivity = Date.now();

    const finalUrl = res.request?.res?.responseUrl || '';
    const success =
      (res.status === 200 || res.status === 302) &&
      (finalUrl.includes('/appointment') || (res.data && res.data.includes('Your appointment')));

    return {
      success,
      status: res.status,
      confirmationUrl: `${AIS_BASE}/schedule/${this.appointmentId}/appointment`,
    };
  }
}

module.exports = { AISClient, CANADA_FACILITIES };
