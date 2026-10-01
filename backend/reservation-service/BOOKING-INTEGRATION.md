> Current additions and API changes: see [README.md](../README.md). Catalogue/history lists are now paginated; paid WL, sessions/logout, admin invitations and demo PDFs are included.

# Booking integration

All payments and refunds in this project are test-mode simulations. A confirmed record is a project demonstration booking, not a real railway ticket.

## Journey and fare APIs

Public `GET /api/reservations/search` accepts `fromStation`, `toStation`, `dateOfJourney` (the boarding date), `seatClass`, and `passengers` (1-6). `GET /api/reservations/quote` accepts the same fields plus `trainNumber`. Both return scheduled departure/arrival instants, the train run date, configured capacity, current available seats, and a price breakdown. Seat availability can change before booking.

Boarding dates must be between today and today plus **two calendar months inclusive**, using India time (`Asia/Kolkata`); this is not a fixed 60-day limit. The departure must still be in the future. Station codes are stripped and uppercased before comparison and booking fingerprints. Same-station journeys are rejected after normalization.

Fare per passenger is `max(minimumFare, baseFare + ratePerKm × distanceKm)`, rounded to two decimal INR places. The total is that fare multiplied by passenger count. The configured rates are project/demo pricing, not official railway tariffs. Every passenger currently pays the same rate. Fare components are snapshotted on booking.

Run date is derived from the boarding stop's departure day. All timetable local times use Asia/Kolkata. `departureDayNumber` supports a stop whose dwell crosses midnight. Unconfigured train inventory, malformed legacy routes, and non-operating dates are unavailable for booking.

## Create and manage a booking

Authenticated `POST /api/reservations/book` requires an `Idempotency-Key` header (8-80 letters, digits, `_` or `-`). Preserve this key when retrying the same request. Reusing it with different passenger/journey details returns 409. Replace the illustrative date below with a date inside the current booking window.

```json
{
  "trainNumber": "12345",
  "fromStation": "AAA",
  "toStation": "CCC",
  "dateOfJourney": "2030-01-02",
  "seatClass": "SL",
  "expectedTotalFare": 120.00,
  "passengers": [{"name": "Example Passenger", "age": 25, "gender": "OTHER"}]
}
```

Use the current quote's total. `expectedTotalFare` detects a changed quote; it is never trusted as the price. A mismatch returns 409. Supply 1-6 non-null passenger objects; invalid/null entries return 400 before persistence. Passenger age is 1-120; gender is MALE, FEMALE or OTHER. The existing `birthPreference` field accepts LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER, NONE, or null/omitted, matching Master List preferences. Empty/unknown/lowercase values are rejected. Preferences are recorded; generated demo seat labels do not guarantee a physical berth layout.

Booking starts `PENDING_PAYMENT`. CNF checkout holds distinct seats for **two minutes** or until boarding time, whichever is earlier. WL checkout holds no seat or active queue position and expires after two minutes or at the WL cutoff, whichever is earlier. MySQL row locking serializes allocations for a train run/class. A seat can be reused on non-overlapping route segments. Expiry is enforced at payment confirmation and query time as well as by a scheduled cleanup, so stopping the cleanup job cannot extend a hold.

`GET /api/reservations/my-bookings` pages the caller's bookings with sorting and optional booking-status/journey-date filters (see README). `GET /api/reservations/pnr/{pnr}` and `DELETE /api/reservations/{pnr}` require owner or admin access. Full-booking cancellation closes at boarding time. Paid CNF cancellation currently deducts 20% (configurable), releases seats, updates passenger statuses, and exposes a pending test refund. Paid WL cancellation receives a full refund; unpaid cancellation has no fee or captured amount to refund. Late or extra captured payments require a full test refund. Refund state becomes PROCESSED only after provider confirmation.

## Payment service contract

The following are direct service endpoints, not gateway routes. Set the same random `RAILWAY_INTERNAL_TOKEN` (at least 32 characters) in payment and reservation services. Requests must include `X-Service-Token`; blank/short tokens fail closed.

- `GET /internal/reservations/{pnr}/payment-context`
- `POST /internal/reservations/{pnr}/payment-confirmation` with `razorpayOrderId`, `razorpayPaymentId`, `amount`, `currency`
- `POST /internal/reservations/{pnr}/refund-status` with `razorpayPaymentId`, `refundStatus`, `refundAmount`

Confirmation is atomic with the booking state and a durable notification outbox entry. The worker retries broker delivery after failure. Configure RabbitMQ publisher confirms/returns; confirmation email is queued only after a successful test payment. Rabbit delivery is at least once.

## Configuration and data

`booking.hold-duration=PT2M`, `booking.cancellation-rate=0.20`, `railway.internal.token=${RAILWAY_INTERNAL_TOKEN:}`, `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`, and RabbitMQ publisher confirms/returns are required in shared configuration. Tests disable the workers and use H2 plus provider/catalogue mocks.

For an existing database, apply the reviewed `src/main/resources/db/manual-migrations/001_booking_lifecycle.sql` followed by `002_waitlist_and_events.sql` if not already applied, after backup and with services stopped. Follow the README for migration order across services. Historical data remains preserved and excluded from new allocation/payment processing as described in those scripts. A new empty development database can use Hibernate `ddl-auto=update`; use validated migrations and `validate` for managed databases. These validation fixes require no additional schema migration.

Run `./Verify-Backend.ps1 -Integration` from the backend root for isolated integration coverage. See README and VALIDATION.md for the distinction between real HTTP/H2 tests and stubbed external boundaries.
