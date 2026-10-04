# Railwize sample database data and test walkthrough

Prepared on 4 October 2026 against the current Railwize entity mappings and validation rules. All stations, coordinates, train services, times and fares here are synthetic test fixtures, not an official railway timetable.

## Files and import

- `load-qa-catalogue.sql`: inserts 10 stations, 15 trains, class/fare configurations, operating days and 30 stops for 14 published timetables. QA110 deliberately has no timetable.
- `catalogue.json`: readable source data for the same catalogue. Train endpoints use station codes here; the train POST API instead requires the actual numeric station IDs returned by your database/API.
- `request-examples.json`: registration template, saved passengers and a two-passenger booking request.

1. Have the normal eight databases and current tables ready. On a fresh installation, run the services once with the existing `DDL_AUTO=update`, then stop them before importing. Do not apply this script to an old, unmigrated schema.
2. Back up your databases if they contain anything you need. The script does not update or delete application rows.
3. Open the entire SQL file in MySQL Workbench and execute it with an account that can create a procedure and insert in `railway_train_db` and `railway_schedule_db` (your local MySQL administrator normally can). The helper procedure is removed after execution. MySQL InnoDB tables are required for transactional rollback.
4. If one of these QA station/train identifiers already exists, import stops and rolls back. Do not remove your existing rows to rerun it. Reuse the dataset if already loaded, or use a separate fresh test database environment. This script is not a reset script.
5. Restart the services and refresh the frontend; the frontend caches the station list during a session.

The import was not executed against your MySQL database. The dataset's station references, time ordering, endpoint nulls and distance ordering were validated offline. Stop on any SQL error rather than continuing with a partial or mismatched schema.

## Dates and accounts

Use D = tomorrow in Asia/Kolkata, D+1 = the day after, and choose a fresh date for each seat-allocation scenario. On the preparation date, D is 2026-10-05 and D+1 is 2026-10-06. When testing later, change the dates. Every booking must remain within today plus two calendar months and before its boarding time.

Register `qa_passenger_a` and `qa_passenger_b` through the UI using two email addresses you control. Use a third mailbox for an admin invitation. Provider-supported plus aliases may be used if they deliver to your inbox and the provider accepts them. The sample password in request-examples.json is only a test value. Never insert plaintext passwords, fake verified flags, access tokens, orders, payments or reservations through SQL.

Create the first administrator using the existing bootstrap flow if needed. Create a second administrator through invitations. Google login should use a Google account you control; usernames/provider identity must come from the real login flow.

Saved passengers:

| Name | Age | Gender | Preference |
|---|---:|---|---|
| Aarav Test | 30 | MALE | LOWER |
| Meera Test | 28 | FEMALE | UPPER |
| Kiran Test | 65 | OTHER | SIDE_LOWER |

Saved-passenger requests use `berthPreference`; booking passengers use the existing API field `birthPreference`. Preferences are recorded but are not an allocation guarantee.

## Stations

| Code | Name | Latitude | Longitude |
|---|---|---:|---:|
| QAMUM | QA Mumbai Central | 19.00 | 72.83 |
| QADAD | QA Dadar Nearby | 19.02 | 72.84 |
| QASRT | QA Surat | 21.17 | 72.83 |
| QABRC | QA Vadodara Junction | 22.31 | 73.18 |
| QAAHM | QA Ahmedabad | 23.03 | 72.58 |
| QAMAN | QA Maninagar Nearby | 23.01 | 72.60 |
| QAPUN | QA Pune | 18.53 | 73.87 |
| QAZERO | QA Missing Coordinates | NULL | NULL |
| QAWLS | QA Waitlist Origin | 28.00 | 78.00 |
| QAWLD | QA Waitlist Destination | 28.90 | 78.00 |

QADAD is approximately 2.5 km from QAMUM; QAMAN is approximately 3.0 km from QAAHM. These are straight-line fixture distances. Use radius 10 km for a nearby-positive test and 1 km for a nearby-negative test.

## Trains and timetables

All trains run daily in SL with one 8-seat coach unless a row specifies otherwise. Times are India local time; distances are cumulative from that train's source. The SQL/JSON contains full stop records. Source arrival and destination departure are NULL.

| Train | Route and times | Purpose |
|---|---|---|
| QA101 | QAMUM 08:00 -> QASRT 11:00/11:10 -> QABRC 13:00; km 0/250/400 | Direct search, intermediate boarding and shared segment inventory |
| QA102 | QABRC 14:30 -> QAAHM 16:30; 100 km | Valid 90-minute connection after QA101 |
| QA103 | QABRC 13:20 -> QAAHM 15:20; 100 km | Invalid 20-minute connection after QA101 |
| QA104 | QABRC 21:30 -> QAAHM 23:30; 100 km | 510-minute connection after QA101, beyond default maximum |
| QA105 | QADAD 09:00 -> QAMAN 17:00; 480 km | Nearby origin AND destination direct alternative |
| QA106 | QAMUM day 1 22:00 -> QABRC day 2 02:00; 400 km | Overnight first leg |
| QA107 | QABRC 03:00 -> QAAHM 05:00; 100 km | Next-day connection after QA106 or QA115 |
| QA108 | QAMUM 08:30 -> QAPUN 11:30; 150 km; MONDAY ONLY | Operating-day filtering |
| QA109 | QAWLS 18:00 -> QAWLD 19:30; 100 km; ONLY TWO SL SEATS | CNF/WL, holds, promotion and refunds |
| QA110 | QAMUM -> QAPUN; no timetable | Unpublished train must not be bookable |
| QA111 | QAZERO 09:00 -> QAAHM 10:00; 100 km | Exact-station search without coordinates |
| QA112 | QADAD 08:30 -> QABRC 12:30; 400 km | Nearby-origin connection to QA102 |
| QA113 | QAMUM 10:00 -> QAPUN 14:00; 200 km; all eight classes | Fare differences and coach/berth labels |
| QA114 | QAZERO 08:00 -> QAMAN 09:00; 10 km | Minimum-fare floor |
| QA115 | QAMUM day 1 21:00 -> QASRT day 1 23:55 / day 2 00:05 -> QABRC day 2 02:00; km 0/250/400 | Departure-day rollover at an intermediate station |

## A. Search and unique journey-planning features

1. Search QAMUM -> QABRC on D, SL, one passenger: QA101, QA106 and QA115 should be available as direct options, subject to current inventory.
2. Search QAMUM -> QAAHM on D, SL. There is no exact direct QA train. Open alternatives with nearby disabled, minimum transfer 60 minutes and maximum 360 minutes.
3. Expect QA101 + QA102 with a 90-minute interchange at QABRC and final arrival 16:30 on D. QA101 + QA103 and QA101 + QA104 must be absent for these settings. Increasing max transfer to 600 may include QA104; the 20-minute transfer stays invalid because the supported minimum cannot go below 30.
4. Expect QA106 + QA107 (and QA115 + QA107) with second-leg boarding on D+1 and a 60-minute transfer. Review both dates before booking.
5. Enable nearby stations with radius 10 km: QA105 is a NEARBY_DIRECT alternative, and QA112 + QA102 is a NEARBY_CONNECTION alternative. The UI should identify the changed boarding/destination station and require the relevant acknowledgement.
6. Reduce radius to 1 km: QADAD/QAMAN alternatives disappear, but exact-station connecting routes can remain.
7. Search QAZERO -> QAAHM directly: QA111 works without coordinates. An alternatives search involving QAZERO should explain the missing source coordinates instead of inventing nearby distances.
8. Search QAMUM -> QAPUN on a future Monday, then Tuesday: QA108 appears only on Monday. QA113 runs daily and may appear on both days. QA110 must not appear as a bookable option.
9. For QA115, search QASRT -> QABRC on D+1: departure is 00:05 on the second run day, and the underlying train run started on D. Verify the calendar/ticket boarding date is D+1.

Swagger example: GET `/api/reservations/alternatives` with fromStation=QAMUM, toStation=QAAHM, dateOfJourney=D, seatClass=SL, passengers=1, nearby=true, radiusKm=10, minTransferMinutes=60, maxTransferMinutes=360.

A connection remains two separate PNRs and payments. The planner does not guarantee the second leg remains available while the first is booked. Do not expect one through-ticket or automatic cancellation of the first leg if the second fails.

## B. Fares and class-specific positions

Fare = max(minimumFare, baseFare + distanceKm * ratePerKm), per passenger.

- QA101 QAMUM -> QABRC: Rs 250 per passenger, Rs 500 for two.
- QA102 QABRC -> QAAHM: Rs 100 per passenger.
- QA101 + QA102: combined displayed fare Rs 350 per passenger, paid as two bookings.
- QA105 QADAD -> QAMAN: Rs 290 per passenger.
- QA109: Rs 70 per passenger, Rs 140 for two.
- QA114: formula subtotal Rs 15, but minimum fare makes the charged quote Rs 50.

QA113, full 200 km journey:

| API class | Seats in one coach | Fare/person INR | Coach label |
|---|---:|---:|---|
| SL | 8 | 150 | S1 |
| CLASS_3A | 8 | 300 | B1 |
| CLASS_2A | 6 | 450 | A1 |
| CLASS_1A | 4 | 600 | H1 |
| CLASS_3E | 9 | 240 | M1 |
| EC | 6 | 360 | E1 |
| CLASS_2S | 6 | 60 | D1 |
| UR | 10 | 30 | GS1 |

Use the actual quote response as expectedTotalFare when creating a booking. Do not replace it with the combined connection fare.

For SL/3A, book six passengers, then two more in another booking on a fresh run date: positions 1-8 are Lower, Middle, Upper, Lower, Middle, Upper, Side lower, Side upper. For 2A, positions 1-6 are Lower, Upper, Lower, Upper, Side lower, Side upper. First AC and 3E currently show position not specified because a detailed cabin/variant layout is not modeled; EC/2S/UR show numbered seats. No booking may contain more than six passengers even when the coach has more seats.

## C. Confirmation, WL and automatic promotion

Use QA109 and one fresh future date for all steps:

1. Passenger A books TWO passengers and completes test payment inside two minutes. Expected: CONFIRMED, two assigned seats, Rs 140 paid.
2. Passenger B books TWO passengers on the SAME train/date/class/segment and pays. Expected: WAITING, no assigned seats, Rs 140 paid. WL must not be displayed as permission to travel.
3. Optional: create another paid ONE-passenger WL booking behind B. It must not jump ahead of B.
4. A previews cancellation. With the default 20% CNF cancellation rate: fee Rs 28, refund Rs 112. Cancel before departure.
5. B's entire booking should automatically become CONFIRMED with both seats, with no second payment or passenger acceptance. The later one-passenger booking remains waiting while B uses both seats.
6. Check B's promotion notification and email/PDF, A's cancellation email, and A's eventual refund-processed email/status. Refund timing depends on test-provider processing and background reconciliation; PENDING is not a completed refund.

If only A and B are used: gross paid fares for this QA109/date are Rs 280; refunded or outstanding refund is Rs 112; net after refunds is Rs 168. Booking count is 2, cancellations 1, cancellation rate 50%. Wait for the backend to confirm the refund before expecting it under processed refunds.

For full WL refund, repeat on another date and cancel B while still WAITING: cancellation fee zero, full Rs 140 refund. If B has already promoted, the CNF cancellation rule applies instead.

## D. Two-minute hold and incomplete payment

On another fresh QA109 date, create a two-passenger booking without paying. Another search for the same segment should see the held capacity. Wait past holdExpiresAt (normally 120 seconds), then reload; the hold becomes EXPIRED and capacity returns. Allow the lifecycle scheduler or an API refresh to process it. Expiry can happen sooner near boarding/WL cutoff.

Trigger an actual Razorpay TEST failure separately and verify failure feedback/email plus retry while the hold remains valid. Merely closing the browser/checkout is not necessarily the same event as a provider-reported failure; unpaid hold expiry is a separate scenario. Never manually set payment success in SQL or invent payment IDs/signatures.

## E. Automatic WL cutoff/refund without waiting until tomorrow

This requires timed activity, not static rows. Through admin screens create a NEW two-seat train on QAWLS -> QAWLD whose departure is about 70 minutes from the current India time and arrival an hour later. Use a new train number, e.g. QA116, and correct day numbers if midnight is crossed. Book the appropriate boarding date. Pay a full-capacity CNF booking and a paid WL booking within the first few minutes. The WL cutoff is one hour before departure, approximately ten minutes after this setup. Keep the CNF booking active. After cutoff and scheduler processing, the WL booking should cancel and request a full test refund. Check notifications, email and final provider-confirmed refund status. Do not modify stored dates, payment states or inventory directly to force this test.

## F. Intermediate-segment inventory

Use QA101/SL on a fresh date (8 seats): pay for six passengers from QAMUM -> QASRT, then six from QASRT -> QABRC. Non-overlapping segments can reuse the same numbered seats. A full-route QAMUM -> QABRC quote should still show two available seats, not eight. A request for three full-route passengers cannot be CNF while these overlapping reservations remain active. For concurrency testing use a fresh two-seat QA109 date and two users attempting to book both seats simultaneously; at most one booking may hold/confirm those same seats for the segment.

## G. Accounts, Master List, email, PDF, calendar and notifications

- Register each test passenger: OTP should arrive; wrong/expired OTP fails; verification activates the account and triggers welcome email.
- Login, reload, open /me/account; email/profile should match that account. While signed in, login/register routes should redirect appropriately.
- Google login, display-username setup/change, logout and password reset should be exercised with real accounts. Reset OTP/email must not be reused as registration OTP.
- Add the three saved passengers above; edit/remove one; choose saved passengers during booking. Switch accounts and confirm the list is private.
- For a paid CNF booking check PNR, train, boarding date/time, coach/berth, fare, attached email ticket and downloaded PDF. Compare both PDFs with the booking details.
- Add an eligible upcoming CNF booking to calendar; check timezone, boarding station and reminder. A downloaded event is static and will not disappear automatically if the ticket is later cancelled.
- Read a notification, refresh, mark all read, open full history, and verify unread count. Sign out and into another account; old user's notifications/chat/recent searches must not leak.
- Make several different searches, reload, repeat one, and switch accounts to check recent-search persistence/deduplication/isolation.
- Email cases: registration OTP, welcome, password reset, admin invitation, CNF booking, paid WL update, WL promotion, payment failure/expiry as applicable, cancellation and completed refund. These require the relevant workflow plus working SMTP/background services; static catalogue insertion sends no emails.

## H. Admin dashboard, reporting and management

- Use passenger accounts above plus paid/cancelled/expired/WL bookings created by the workflows. Dashboard counts should reflect actual records; existing non-QA data also contributes to all-time totals.
- Search users by username/email; inspect history, block a test passenger and verify protected access fails, then reactivate and log in again. Admin/self accounts must remain protected from the passenger-block action.
- Filter bookings by QA109, date, status and account; compare with the known sequence in section C.
- Make paid bookings on at least two journey dates; also use a date in the next calendar month within the booking window to exercise monthly reports. Reports group by JOURNEY DATE, not payment date.
- Export users, bookings, revenue and cancellations CSV. Check headers, row counts, INR totals and last-applied filters.
- Create enough normal records or use a small page size in Swagger to test pagination and sorting. No need to fabricate 100 bookings in SQL.
- Test station/train editing with a newly created unused QA record. Publishing requires valid fare/capacity/operating days and correct source/destination timetable stops. Published timetables are immutable; do not try to reset the fixture route after bookings.
- Accept an admin invitation with a real inbox; verify ordinary registration cannot choose ADMIN and passenger accounts cannot use admin APIs.

## I. Chatbot

After creating real bookings for passenger A, ask: "Show my bookings", "How many bookings do I have?", "Which tickets have I cancelled?", and "When is my next journey?" Compare with the account's booking list. Ask for a specific owned PNR, then a PNR belonging to B; private cross-account details must not be exposed. Ask "Find trains from QAMUM to QAPUN on <D>, class SL, for two passengers" and select a passenger count before using Book. Ask "How long is the payment hold?" and "What is the cancellation policy?" for RAG. Follow up within the conversation, open/close the side panel, and expand to /assistant to check continuity. Real AI replies require the configured Gemini model/quota. Cache timing is not guaranteed for every similar question; live/private booking answers must not be served from a shared policy cache.

## J. Negative/API tests

Use Swagger or browser dev tools for requests that the normal form blocks. Expected: clear rejection, no unwanted booking/payment change.

| Input/action | Expected |
|---|---|
| Same origin/destination, including spaces/case differences | Rejected after normalization |
| Unknown station/train | Not found or validation failure, no fabricated route |
| Yesterday or impossible date | Rejected |
| Today + two calendar months | Allowed if operating, departure future and otherwise eligible |
| Day after that boundary | Rejected |
| Passenger count 0, -1, 1.5 or 7 | Rejected |
| Passengers [] or [null] | Rejected |
| Age 0 or 121 in Master List | Rejected |
| Unsupported berth preference or class | Rejected |
| Fare different from current quote | Rejected; fetch a fresh quote |
| Repeated booking request with the same idempotency key and identical body | Same booking, not a duplicate |
| Same key with changed passenger/body | Conflict |
| Two paid tickets on the same reserved seat/overlapping segment | Must never happen |
| Another user's PNR/profile/download/notifications | Access denied or not found |
| Passenger calls admin endpoint | Forbidden |
| Missing/expired/revoked session | Unauthorized; frontend may attempt its normal refresh flow |
| Duplicate station/train, timetable stop, or invalid stop ordering | Rejected |
| Source arrival or final departure supplied | Rejected by backend; frontend disables/clears those fields |
| Decreasing distance, impossible overnight chronology | Rejected |
| Only one coordinate, latitude beyond +/-90, longitude beyond +/-180 | Rejected |
| Nearby radius outside 1-100 or transfer bounds invalid | Rejected |
| No timetable (QA110) | Unavailable for booking |
| Tampered Razorpay signature or another PNR's order | Must not confirm payment/booking |
| Repeated cancel/payment callback | No duplicated cancellation refund or seat allocation |

Service outages, circuit-breaker recovery, provider webhook retries and broker retry/deduplication need controlled dependency failures or the automated suites. No set of database rows alone tests those behaviours. Run backend Verify-Backend.ps1 and frontend npm test as well. This dataset has not been pushed to GitHub or applied to a live database.
