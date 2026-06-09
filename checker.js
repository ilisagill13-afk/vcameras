const axios = require('axios');

const LOCATIONS = [
  { id: 'new-delhi', name: 'New Delhi', country: 'India', provider: 'cgi' },
  { id: 'mumbai', name: 'Mumbai', country: 'India', provider: 'cgi' },
  { id: 'chennai', name: 'Chennai', country: 'India', provider: 'cgi' },
  { id: 'hyderabad', name: 'Hyderabad', country: 'India', provider: 'cgi' },
  { id: 'kolkata', name: 'Kolkata', country: 'India', provider: 'cgi' },
  { id: 'islamabad', name: 'Islamabad', country: 'Pakistan', provider: 'cgi' },
  { id: 'karachi', name: 'Karachi', country: 'Pakistan', provider: 'cgi' },
  { id: 'lahore', name: 'Lahore', country: 'Pakistan', provider: 'cgi' },
  { id: 'dhaka', name: 'Dhaka', country: 'Bangladesh', provider: 'cgi' },
  { id: 'mexico-city', name: 'Mexico City', country: 'Mexico', provider: 'cgi' },
  { id: 'london', name: 'London', country: 'UK', provider: 'ustraveldocs' },
  { id: 'frankfurt', name: 'Frankfurt', country: 'Germany', provider: 'ustraveldocs' },
  { id: 'paris', name: 'Paris', country: 'France', provider: 'ustraveldocs' },
  { id: 'toronto', name: 'Toronto', country: 'Canada', provider: 'ustraveldocs' },
  { id: 'sydney', name: 'Sydney', country: 'Australia', provider: 'ustraveldocs' },
];

const VISA_TYPES = [
  { id: 'B1/B2', name: 'B1/B2 - Business/Tourism' },
  { id: 'F1', name: 'F1 - Student' },
  { id: 'J1', name: 'J1 - Exchange Visitor' },
  { id: 'H1B', name: 'H1B - Specialty Occupation' },
  { id: 'L1', name: 'L1 - Intracompany Transfer' },
  { id: 'O1', name: 'O1 - Extraordinary Ability' },
];

function getLocations() {
  return { locations: LOCATIONS, visaTypes: VISA_TYPES };
}

// Generates realistic-looking mock slot data for demonstration.
// Replace this function body with real HTTP requests + parsing once
// you have valid CGI/USTravelDocs session credentials.
async function checkSlots(locationId, visaType) {
  const location = LOCATIONS.find(l => l.id === locationId);
  if (!location) throw new Error(`Unknown location: ${locationId}`);

  // Simulate network latency
  await new Promise(r => setTimeout(r, 300 + Math.random() * 700));

  const today = new Date();
  const slots = [];

  for (let i = 1; i <= 30; i++) {
    const date = new Date(today);
    date.setDate(today.getDate() + i);
    const dayOfWeek = date.getDay();
    if (dayOfWeek === 0 || dayOfWeek === 6) continue; // skip weekends

    const dateStr = date.toISOString().split('T')[0];
    // Randomly mark some slots as available (roughly 20% chance per day)
    const available = Math.random() < 0.2;

    if (available) {
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

module.exports = { checkSlots, getLocations };
