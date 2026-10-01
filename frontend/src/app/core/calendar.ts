import { Booking } from './models';

type CalendarBooking = Pick<Booking, 'pnr' | 'trainNumber' | 'fromStation' | 'toStation' | 'seatClass' | 'departureAt' | 'status'>;

function departureTime(value: string): number {
  // Never interpret a timestamp without an offset in the browser's local timezone.
  return /(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? Date.parse(value) : NaN;
}

export function canAddToCalendar(booking: CalendarBooking | null, now = Date.now()): boolean {
  return !!booking && booking.status === 'CONFIRMED' && departureTime(booking.departureAt) > now;
}

function calendarText(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/\r\n|\r|\n/g, '\\n').replace(/;/g, '\\;').replace(/,/g, '\\,');
}

function foldLine(value: string): string {
  const encoder = new TextEncoder();
  let folded = '', length = 0;
  for (const character of value) {
    const size = encoder.encode(character).length;
    if (length + size > 75) { folded += '\r\n '; length = 1; }
    folded += character; length += size;
  }
  return folded;
}

function timestamp(date: Date): string {
  return date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
}

export function bookingCalendar(booking: CalendarBooking, now = new Date()): string {
  if (!canAddToCalendar(booking, now.getTime())) {
    throw new Error('Calendar reminders are available for confirmed bookings before departure. Refresh your booking to check its status.');
  }
  const description = `PNR: ${booking.pnr}\nTrain: ${booking.trainNumber}\nClass: ${booking.seatClass.replace('CLASS_', '')}\nDeparture reminder only; check your ticket for journey details.\nThis calendar entry does not update automatically. Remove it if you cancel your booking.`;
  return [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Railwize//Departure Reminder//EN', 'CALSCALE:GREGORIAN',
    'BEGIN:VEVENT', `UID:railwize-${encodeURIComponent(booking.pnr)}@calendar.railwize.local`,
    `DTSTAMP:${timestamp(now)}`, `DTSTART:${timestamp(new Date(departureTime(booking.departureAt)))}`,
    `SUMMARY:${calendarText(`Train ${booking.trainNumber}: ${booking.fromStation} → ${booking.toStation} · Departure`)}`,
    `LOCATION:${calendarText(booking.fromStation)}`, `DESCRIPTION:${calendarText(description)}`,
    'STATUS:CONFIRMED', 'CLASS:PRIVATE', 'TRANSP:TRANSPARENT',
    'BEGIN:VALARM', 'TRIGGER:-PT1H', 'ACTION:DISPLAY',
    `DESCRIPTION:${calendarText(`Your train from ${booking.fromStation} departs in one hour.`)}`,
    'END:VALARM', 'END:VEVENT', 'END:VCALENDAR'
  ].map(foldLine).join('\r\n') + '\r\n';
}

export function downloadCalendar(booking: CalendarBooking): void {
  const blob = new Blob([bookingCalendar(booking)], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `Railwize-${booking.pnr.replace(/[^A-Za-z0-9_-]/g, '_')}.ics`;
  document.body.appendChild(link);
  try { link.click(); }
  finally { link.remove(); setTimeout(() => URL.revokeObjectURL(url), 30000); }
}
