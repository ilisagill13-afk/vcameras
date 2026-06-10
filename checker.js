const axios = require('axios');
const { CANADA_FACILITIES } = require('./ais-checker');

const LOCATIONS = [
  // Canada — served by AIS (ais.usvisa-info.com/en-ca)
  { id: 'calgary',     name: 'Calgary',     country: 'Canada', provider: 'ais' },
  { id: 'halifax',     name: 'Halifax',     country: 'Canada', provider: 'ais' },
  { id: 'montreal',    name: 'Montreal',    country: 'Canada', provider: 'ais' },
  { id: 'ottawa',      name: 'Ottawa',      country: 'Canada', provider: 'ais' },
  { id: 'quebec-city', name: 'Quebec City', country: 'Canada', provider: 'ais' },
  { id: 'toronto',     name: 'Toronto',     country: 'Canada', provider: 'ais' },
  { id: 'vancouver',   name: 'Vancouver',   country: 'Canada', provider: 'ais' },
  // Other countries — CGI Federal / USTravelDocs (mock until credentials added)
  { id: 'new-delhi',   name: 'New Delhi',   country: 'India',      provider: 'cgi' },
  { id: 'mumbai',      name: 'Mumbai',      country: 'India',      provider: 'cgi' },
  { id: 'islamabad',   name: 'Islamabad',   country: 'Pakistan',   provider: 'cgi' },
  { id: 'karachi',     name: 'Karachi',     country: 'Pakistan',   provider: 'cgi' },
  { id: 'dhaka',       name: 'Dhaka',       country: 'Bangladesh', provider: 'cgi' },
  { id: 'london',      name: 'London',      country: 'UK',         provider: 'cgi' },
  { id: 'frankfurt',   name: 'Frankfurt',   country: 'Germany',    provider: 'cgi' },
];

const VISA_TYPES = [
  { id: 'B1/B2', name: 'B1/B2 - Business/Tourism' },
  { id: 'F1',    name: 'F1 - Student' },
  { id: 'J1',    name: 'J1 - Exchange Visitor' },
  { id: 'H1B',   name: 'H1B - Specialty Occupation' },
  { id: 'L1',    name: 'L1 - Intracompany Transfer' },
  { id: 'O1',    name: 'O1 - Extraordinary Ability' },
];

function getLocations() {
  return { locations: LOCATIONS, visaTypes: VISA_TYPES };
}

// Real AIS slot check for Canada locations
async function checkAISSlots(locationId, visaType, aisClient) {
  const facility = CANADA_FACILITIES[locationId];
  if (!facility) throw new Error(`Unknown Canada location: ${locationId}`);

  const dates = await aisClient.getAvailableDates(facility.id);
  const slots = [];

  for (const d of dates.slice(0, 60)) {
    if (!d.date) continue;
    const times = await aisClient.getAvailableTimes(facility.id, d.date);

    if (times.length > 0) {
      for (const time of times) {
        slots.push({
          date: d.date,
          time,
          available: true,
          location: facility.name,
          visaType,
          facilityId: facility.id,
          bookingUrl: `https://ais.usvisa-info.com/en-ca/niv/schedule/${aisClient.appointmentId}/appointment`,
        });
      }
    } else {
      slots.push({ date: d.date, available: false });
    }
  }

  return slots;
}

// Mock fallback for non-AIS locations
async function checkMockSlots(locationId, visaType) {
  const location = LOCATIONS.find(l => l.id === locationId);
  await new Promise(r => setTimeout(r, 300 + Math.random() * 500));

  const today = new Date();
  const slots = [];

  for (let i = 1; i <= 30; i++) {
    const date = new Date(today);
    date.setDate(today.getDate() + i);
    if (date.getDay() === 0 || date.getDay() === 6) continue;

    const dateStr = date.toISOString().split('T')[0];
    if (Math.random() < 0.18) {
      const hour = 8 + Math.floor(Math.random() * 8);
      const minute = Math.random() < 0.5 ? '00' : '30';
      slots.push({
        date: dateStr,
        time: `${String(hour).padStart(2, '0')}:${minute}`,
        available: true,
        location: location.name,
        visaType,
        bookingUrl: `https://cgifederal.secure.force.com/?country=${location.country}&language=English`,
      });
    } else {
      slots.push({ date: dateStr, available: false });
    }
  }

  return slots;
}

// Earliest available slot for one location (lightweight: only the first
// available date is expanded into times on the AIS path)
async function findEarliestSlot(locationId, visaType, aisClient = null) {
  const location = LOCATIONS.find(l => l.id === locationId);
  if (!location) throw new Error(`Unknown location: ${locationId}`);

  if (location.provider === 'ais' && aisClient) {
    const facility = CANADA_FACILITIES[locationId];
    const dates = await aisClient.getAvailableDates(facility.id);
    const first = dates.find(d => d.date);
    if (!first) return { location, slot: null, openDates: 0, live: true };

    const times = await aisClient.getAvailableTimes(facility.id, first.date);
    if (times.length === 0) return { location, slot: null, openDates: dates.length, live: true };

    return {
      location,
      openDates: dates.length,
      live: true,
      slot: {
        date: first.date,
        time: times[0],
        available: true,
        location: facility.name,
        visaType,
        facilityId: facility.id,
        bookingUrl: `https://ais.usvisa-info.com/en-ca/niv/schedule/${aisClient.appointmentId}/appointment`,
      },
    };
  }

  const slots = await checkMockSlots(locationId, visaType);
  const available = slots.filter(s => s.available);
  return { location, slot: available[0] || null, openDates: available.length, live: false };
}

async function checkSlots(locationId, visaType, aisClient = null) {
  const location = LOCATIONS.find(l => l.id === locationId);
  if (!location) throw new Error(`Unknown location: ${locationId}`);

  if (location.provider === 'ais' && aisClient) {
    return checkAISSlots(locationId, visaType, aisClient);
  }
  return checkMockSlots(locationId, visaType);
}

module.exports = { checkSlots, findEarliestSlot, getLocations, LOCATIONS };
