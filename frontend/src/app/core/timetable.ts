import { Stop } from './models';

/** Normalize the endpoint-only fields and validate the same route rules as schedule-service. */
export function timetablePayload(stops: Stop[]): Stop[] {
  if (stops.length < 2 || stops.length > 200) throw new Error('A timetable needs 2–200 stops.');
  const rows = stops.map((s, i) => ({
    ...s, stationCode: s.stationCode.trim().toUpperCase(), stopSequence: i + 1,
    arrivalTime: i === 0 ? null : (s.arrivalTime || null),
    departureTime: i === stops.length - 1 ? null : (s.departureTime || null),
    dayNumber: i === 0 ? 1 : s.dayNumber,
    departureDayNumber: i === 0 ? 1 : i === stops.length - 1 ? s.dayNumber : s.departureDayNumber,
    distanceKm: i === 0 ? 0 : s.distanceKm
  }));
  const codes = new Set<string>();
  let previousDeparture = -1, previousDistance = -1;
  function seconds(day: number, time: string | null, label: string): number {
    if (!time || !/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(time)) {
      throw new Error(label + ': enter a valid time.');
    }
    const [h, m, s = 0] = time.split(':').map(Number);
    return (day - 1) * 86400 + h * 3600 + m * 60 + s;
  }
  rows.forEach((s, i) => {
    const label = `Stop ${i + 1} (${s.stationCode || 'choose a station'})`;
    if (!/^[A-Z0-9]{1,10}$/.test(s.stationCode) || codes.has(s.stationCode)) {
      throw new Error(label + ': choose a unique valid station.');
    }
    codes.add(s.stationCode);
    if (![s.dayNumber, s.departureDayNumber].every(d => Number.isInteger(d) && d >= 1 && d <= 30)
        || s.departureDayNumber < s.dayNumber) {
      throw new Error(label + ': arrival/departure days must be 1–30, with departure on or after arrival day.');
    }
    if (!Number.isInteger(s.distanceKm) || s.distanceKm <= previousDistance || s.distanceKm > 20000) {
      throw new Error(label + ': cumulative distance must increase, using whole kilometres up to 20000.');
    }
    const arrival = i > 0 ? seconds(s.dayNumber, s.arrivalTime, label + ' arrival') : -1;
    const departure = i < rows.length - 1 ? seconds(s.departureDayNumber, s.departureTime, label + ' departure') : -1;
    if (i > 0 && arrival <= previousDeparture) throw new Error(label + ': arrival must follow the previous departure. Increase the day for overnight journeys.');
    if (i > 0 && i < rows.length - 1 && departure < arrival) throw new Error(label + ': departure cannot be before arrival.');
    previousDeparture = departure;
    previousDistance = s.distanceKm;
  });
  return rows;
}
