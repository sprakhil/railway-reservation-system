> Current additions and API changes: see [README.md](../README.md). Catalogue/history lists are now paginated; paid WL, sessions/logout, admin invitations and demo PDFs are included.

# Timetable setup and API

Public `GET /api/schedules/{trainNumber}` returns ordered stops. Administrator-only `POST /api/schedules/{trainNumber}` publishes a complete timetable. Repeating an identical timetable is idempotent. A changed published timetable returns 409 because changing times, distances or route segments would invalidate current bookings.

The train and every station must already exist in train-service. First/last stop station IDs must match the train's configured endpoints. A catalog outage returns 503; an unknown train/station returns 404. No partial timetable is saved.

Submit 2–200 unique stations, in consecutive `stopSequence` order starting at 1. Source has distance 0, day 1, a departure time and no arrival. Destination has an arrival and no departure. Every intermediate stop has both. Cumulative integer `distanceKm` values strictly increase. Each arrival must follow the previous departure, and a stop's departure cannot precede its arrival. Day values range from 1 to 30.

`dayNumber` describes the arrival day; optional `departureDayNumber` defaults to `dayNumber` for compatibility. Explicitly supply it when a stop spans midnight. Returned stops always expose an effective `departureDayNumber`.

```json
[
  {"stationCode":"NDLS","stopSequence":1,"dayNumber":1,"distanceKm":0,"departureTime":"22:00:00"},
  {"stationCode":"CNB","stopSequence":2,"dayNumber":1,"departureDayNumber":2,"distanceKm":400,"arrivalTime":"23:55:00","departureTime":"00:05:00"},
  {"stationCode":"LKO","stopSequence":3,"dayNumber":2,"distanceKm":500,"arrivalTime":"03:00:00"}
]
```

Before deployment, back up the schedule database, pause bookings, audit legacy route order, distances and day/time chronology, and run `src/main/resources/db/manual-migrations/001_departure_day.sql` once. If Hibernate already created the column or named constraint, omit the corresponding ALTER statement. The backfill preserves same-day meaning; explicitly correct any existing stop that crosses midnight before reopening bookings. A train number/stop sequence must be unique, as before. The upgrade also adds train number/station code uniqueness: its duplicate audit must return no rows, and the constraint fails safely until duplicate legacy stops are resolved. Do not automatically delete or renumber legacy stops referenced by bookings. The new service never deletes/replaces a published route.

Integration tests use isolated H2 with a mocked train catalog. Config Server and discovery are disabled during `mvn test`.
