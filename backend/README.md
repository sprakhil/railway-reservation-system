# Railway reservation backend — test payments

This is the backend only. Angular will be a separate sibling project. All checkout and refund operations are restricted to **Razorpay test mode**. The code rejects live keys. These bookings do not issue real railway tickets or move real money.

For database SQL, startup commands, shared Swagger authorization, all request JSON bodies and the manual test sequence, see the [complete local startup and Swagger walkthrough](#complete-local-startup-and-swagger-walkthrough).

## Implemented behavior

- Search by boarding station, destination, boarding date, class and passenger count. Timetables support overnight journeys and midnight stops.
- Admin-configured class capacity, operating days and demo fare rates. Existing unconfigured trains remain unavailable until configured.
- Backend fare quotes from route distance and class rates, with exact INR decimals and saved price breakdowns.
- Pending bookings with **two-minute holds**, whole-booking CNF/WL allocation, overlapping-route checks, concurrent allocation protection and retry-safe booking keys.
- Confirmation only after verified captured test payment. Browser closure, duplicate callbacks, webhook retries and interrupted provider responses are reconciled.
- Paid WL bookings promote automatically in strict whole-booking queue order. Remaining WL bookings close one hour before boarding and receive full test refunds.
- Full-booking cancellation before boarding, with a preview endpoint. The configurable CNF fee is 20%; unpaid/WL cancellations have no fee. Late and extra captures get full test refunds. Refund statuses reflect provider results.
- Owner/admin access checks, secure Google login with browser-bound one-time exchange, purpose-separated OTPs, attempt limits and refresh-token revocation after password reset.
- Durable lifecycle notification outbox/inbox, email retries and duplicate handling. All emails explicitly describe test bookings.
- Current/all-device logout, controlled admin invitations, paginated catalogue/history, a passenger Master List and owner-protected demo PDF downloads.
- A gateway Swagger explorer with shared bearer authorization, service-specific descriptions, request examples, public/admin/owner access guidance and hidden internal service operations.

## Booking, payment and the waitlist

- New searches, quotes and bookings accept boarding dates from today through **today plus two calendar months, inclusive**, calculated in `Asia/Kolkata`. For example, 23 September permits dates through 23 November. Calendar month-end adjustment applies (31 December 2027 permits dates through 29 February 2028). This is not a fixed 60-day window. Boarding time must still be in the future; existing booking history and idempotent replays remain accessible.
- Station codes are stripped of surrounding whitespace and converted to uppercase before search/quote comparisons, booking fingerprints, station catalogue lookup/create/update, and timetable publication. Selecting the same station after normalization is rejected. Station names are display text; APIs accept station codes.
- A passenger list must contain 1–6 actual passenger objects: null elements are rejected with a 400 field-validation response before catalogue access or persistence. Booking `birthPreference` and Master List `berthPreference` accept the same values: LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER, NONE, or null/omitted. Empty, unknown or lowercase preference values are rejected. The existing booking field spelling is retained for compatibility.
- A booking contains 1–6 passengers on the same train run, class and journey segment. Each passenger receives their own seat, but the **entire booking** is CNF or WL. No RAC, mixed allocation or individual-passenger cancellation.
- `CONFIRMED` is the stored/API status for CNF; `WAITING` is WL. Lifecycle states also include `PENDING_PAYMENT`, `EXPIRED` and `CANCELLED`. Payment/refund status is separate.
- Quotes include `availableSeats`, `suggestedAllocation`, `waitlistOpen` and `waitlistDeadline`. Quotes are advisory; booking rechecks availability under the inventory lock. The booking response's `allocation` (`CNF`/`WL`) identifies what the user is paying for. Show it before checkout.
- `POST /api/reservations/book` still requires a unique `Idempotency-Key` and the server quote's `expectedTotalFare`. The two-minute payment deadline applies to both CNF and WL checkout; CNF seats are held during that time, WL checkout reserves no seat or queue place. Holds close earlier if boarding/WL cutoff is reached.
- Payment covers the whole booking, including WL. Only captured, verified test payment activates a CNF ticket or enrolls a WL booking. Late payment after expiry receives a full test refund.
- Paid WL priority uses increasing booking ID (booking creation order) within **train run + class**, including all route segments. Unpaid bookings are not eligible. Queue position counts whole bookings, not passengers. A pending WL checkout that is paid later joins in its creation order among bookings still waiting; existing confirmations are never reversed.
- Promotion is strict FIFO: the oldest paid WL booking must fit in full on its whole journey segment. A seat must be available throughout that segment. Smaller/later bookings do not bypass it, even on other segments of the same run/class. This deliberately sacrifices some seat utilization for a simple predictable rule.
- Cancellation, payment confirmation, holds expiring, WL expiry and promotion all use the same inventory lock before updating reservations. New bookings first process due expiry/promotion. The scheduler checks every ten seconds; API reads/payment checks also refresh lifecycle where relevant.
- WL closes **one hour before each booking's boarding time**, configurable through `booking.waitlist-cutoff=PT1H`. Every remaining paid WL booking is cancelled and receives a full test refund. No new WL checkout is accepted after the cutoff. The cutoff wins if seats are released exactly at the deadline.
- `GET /api/reservations/{pnr}/cancellation-preview` returns the current full-booking cancellation fee/refund estimate. Cancellation uses the current status at the time it executes: pending and WL cancellations have no fee; paid CNF cancellations retain the configurable 20% demo fee. A WL booking promoted between preview and cancellation is therefore subject to the CNF fee. The frontend should refresh the preview before confirming.
- `DELETE /api/reservations/{pnr}` cancels the whole booking. Repeated requests do not duplicate a refund. Test refunds are asynchronous; `PENDING` does not mean the provider has completed them. Payment reconciliation runs independently, including when a browser is closed.
- A payment response's `paymentStatus=SUCCESS` means the booking accepted the payment. Use `bookingStatus`, not payment success, to distinguish CNF and WL. `payments[].bookingConfirmed` is false for WL and cancelled bookings.

## Important emails

Durable outbox events cover payment received with CNF/WL status, WL promotion with seats, cancellation, WL cutoff, refund initiation/completion, and a refund failure needing attention. All messages identify the booking as a demo. No email is sent merely because an unpaid checkout was created.

The notification consumer durably records each event ID before acknowledging it. A worker sends email and retries failures up to five attempts with increasing delay. Duplicate received IDs and successfully sent events are suppressed. Exhausted deliveries remain in `notification_deliveries` for administrator investigation. SMTP cannot guarantee exactly-once delivery across a crash after sending but before committing; a duplicate is possible in that narrow window. An email failure never reverses a successful booking/payment.

## Logout and admin invitations

- `POST /api/auth/logout`: bearer authentication, no body; revokes the current session. `POST /api/auth/logout-all` revokes all sessions for that account. Both return 204. Frontends must also clear their access/refresh tokens and cached user data.
- Multiple logins can coexist; a new login does not silently log out another device. No new login/request throttling was added. Existing OTP safeguards remain.
- Access tokens contain a session ID. Resource services validate it against the auth service on every authenticated request. Revoked, expired, inactive-user and stale-role sessions are rejected. There is no positive session cache, so later requests are rejected after logout; an already executing request is not rolled back. If auth is unavailable, protected requests fail closed with 503. Auth must run for authenticated resource calls.
- Set `railway.auth.url` through `AUTH_SERVICE_URL` (default `http://localhost:8081`) and configure the shared `RAILWAY_INTERNAL_TOKEN`. The internal session-check endpoint is not gateway-routed and requires this service credential. Existing pre-upgrade tokens require sign-in again.
- First admin: use the explicit bootstrap configuration once, then disable it. Public registration/Google-created accounts remain passengers; roles never come from a registration request.
- An authenticated admin issues `POST /api/auth/admin/invitations` with `{"email":"person@example.test"}`. It returns invitation metadata; the secret is delivered only by email. Invitations are random, stored hashed, expire in 24 hours and are single-use.
- Recipients first register/verify their invited address or sign in with its existing verified account, then submit `POST /api/auth/admin-invitations/accept` with `{"token":"..."}` and their bearer token. Only the invited verified account can accept. Existing sessions are revoked on role elevation; sign in again for an admin token.
- Admins can page through `GET /api/auth/admin/invitations?page=0&size=20` and revoke an unused invitation using `DELETE /api/auth/admin/invitations/{id}`. Invitation records retain issuer, acceptance and revocation metadata for audit.
- The invitation email points to the future Angular `/admin/accept-invitation#token=...` route. Until Angular exists, copy the fragment token into the acceptance API. Configure `app.admin-invitation-uri` through `ADMIN_INVITATION_URI`. The future `/admin` portal uses the same login; backend admin authorization is already enforced.

## History, catalogue and saved passengers

List responses use `{content,page,size,totalElements,totalPages}`, zero-based pages and a maximum size of 100.

| Endpoint | Defaults / supported parameters |
| --- | --- |
| `GET /api/reservations/my-bookings` | `page=0&size=20&sort=bookingTime&direction=desc`; sort: bookingTime/dateOfJourney/trainNumber/status/pnr; optional status (CNF/WL or stored lifecycle value), inclusive fromDate/toDate applied to **journey date** |
| `GET /api/trains` | `page=0&size=20&sort=trainNumber&direction=asc`; sort: trainId/trainNumber/trainName |
| `GET /api/stations` | `page=0&size=20&sort=stationCode&direction=asc`; sort: stationId/stationCode/stationName |
| `GET /api/users/me/passengers` | `page=0&size=20`, name order |

Invalid page sizes, directions, unknown sort fields/statuses and reversed date ranges return 400. Stable ID tie-breakers avoid ambiguous order for equal values. The reservation catalogue client now reads train pages; it does not expect the old bare array. Journey search still returns matching journey quotes; timetable stops remain an ordered route list.

Saved passengers:

```json
{"name":"Demo Traveller","age":25,"gender":"OTHER","berthPreference":"LOWER"}
```

Use `POST /api/users/me/passengers`, `PUT /api/users/me/passengers/{id}` and `DELETE /api/users/me/passengers/{id}`. Only the owning user can read/change their records. Accepted preferences: LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER, NONE (or null). Ages are 1–120 and must be reviewed when reusing an older saved record; this version stores age, not date of birth.

To book, retrieve saved passengers and copy selected fields into `passengers[]` in the normal booking request. The existing booking field is named `birthPreference` (map saved `berthPreference` to it). The backend validates and saves independent passenger snapshots; later Master List edits/deletion cannot alter an existing booking. Preferences are informational, not guaranteed allocations.

## Downloadable demo PDF

`GET /api/reservations/{pnr}/ticket.pdf` requires the booking owner's or an admin's bearer token and verified test payment. It returns an attachment with `Cache-Control: no-store`. CNF/WL/cancellation and refund status reflect the moment of download; later updates require another download. PDFs explicitly say **DEMO BOOKING - NOT VALID FOR TRAVEL**.

Apache PDFBox embeds the bundled Lato Regular and Bold fonts from `reservation-service/src/main/resources/ticket/fonts/`. Their SIL Open Font License is included as `OFL.txt`, and asset provenance is in `ticket/ASSETS.txt`. Leave `PDF_FONT_PATH` blank to use these bundled fonts; PDF generation requires no internet access. `booking.pdf-font` / `PDF_FONT_PATH` remains an optional regular-font override. Characters absent from the selected font are represented as `[U+XXXX]` instead of silently lost; complex-script shaping remains a limitation. Tickets include the Railwize header, PNR/status, route and India boarding time, passenger/seat table, fare/refund summary, download timestamp and page numbering. Long names wrap and overflow is continued on additional pages.

## Error contract

Every service has exception handling using this JSON shape; security failures and gateway routing failures use it too. Internal exception messages and stack traces are not returned.

```json
{"code":"BAD_REQUEST","status":400,"message":"Request validation failed","fieldErrors":{"passengers[0].age":"must be greater than or equal to 1"},"timestamp":"2030-01-01T00:00:00Z","requestId":"a-generated-request-id"}
```

`X-Request-ID` is returned and forwarded by the gateway. CORS exposes it and PDF `Content-Disposition`. Logging uses the ID and exception class without logging submitted credentials. Client field messages are in `fieldErrors`; no successful-response wrapping is introduced.

## Local prerequisites

Use Java 21 or later, Maven 3.9+, MySQL 8, and RabbitMQ. Create the eight databases named in `config-server/src/main/resources/config-repo/*-service.properties` (`railway_auth_db`, `railway_user_db`, `railway_train_db`, `railway_schedule_db`, `railway_reservation_db`, `railway_pnr_db`, `railway_payment_db`, `railway_notification_db`).

1. Run `./New-DevEnvironment.ps1` using PowerShell 7. It creates `.env` with random local config/discovery/JWT/internal-service secrets and never overwrites an existing file.
2. Edit `.env` with your local MySQL settings. Add an SMTP test account for OTP/email delivery, Google web OAuth credentials, and Razorpay **test** credentials when testing those integrations. Keep `.env` private and out of Git. No original credentials are bundled.
3. For an existing database, review the migration section below **before startup**. New empty development databases can use the default `DDL_AUTO=update`; managed existing schemas should use reviewed migrations and `DDL_AUTO=validate`.
4. In separate terminals, run `./Run-Service.ps1 config-server`, then `eureka-server`. Start `train-service`, `schedule-service`, `auth-service`, `user-service`, `pnr-service`, `reservation-service`, `payment-service`, `notification-service`, `chatbot-service`, and `api-gateway` using the same script.
5. The gateway is on port 8080; the Angular frontend uses port 4200. Swagger is available through the gateway. Individual service ports are 8081–8089, with configuration on 8888 and discovery on 8761.

For the first admin account, explicitly enable `BOOTSTRAP_ADMIN_ENABLED` and provide username, email and a strong password in `.env`, start auth-service once, then disable bootstrap. There is no built-in admin password.

## Google and Razorpay setup

Register `http://localhost:8080/api/auth/google/callback` as the Google web client's authorized redirect URI. Angular will navigate to `/api/auth/google/start`, then exchange the one-time callback code using credentials/cookies. Details and account-linking behavior: `auth-service/AUTH-INTEGRATION.md`.

Use `RAZORPAY_TEST_KEY_ID=rzp_test_...` with the matching test secret. The application has no live-mode switch. Register a test webhook to `/api/payments/webhook` if the gateway has a reachable HTTPS address. The scheduled reconciler also recovers missing browser callbacks by querying test-provider state. Localhost webhook delivery requires your own reachable test endpoint; no tunnel or public deployment is created by these changes.

Credentials can be left empty while running offline tests. Missing test credentials make checkout unavailable; the backend never fabricates a successful payment. A real browser Google login and Razorpay dashboard test checkout require your configured accounts and are separate from the automated mocked-provider tests.

## API workflow

1. Configure stations, a train with class settings/operating days, and a valid timetable using admin APIs.
2. Call `GET /api/reservations/search` and `/quote` with source, destination, date, class and passenger count.
3. Submit `POST /api/reservations/book` with the quoted `expectedTotalFare` and an `Idempotency-Key` header. The server returns a PNR, CNF/WL allocation, saved fare and `holdExpiresAt`. CNF checkout holds seats; WL checkout holds no seats or queue position.
4. Submit `POST /api/payments/create-order` with only the PNR. Use its `keyId`, `razorpayOrderId`, `amountInPaise` and `currency` in Razorpay test checkout.
5. Submit the signed checkout result to `/api/payments/verify`. Poll `/api/payments/{pnr}/status` after interruption. Show CNF only when the booking becomes `CONFIRMED`; a successfully paid `WAITING` booking remains WL until automatic promotion.
6. Cancel with `DELETE /api/reservations/{pnr}`. Query booking/payment status for refund progress.

See `reservation-service/BOOKING-INTEGRATION.md`, `payment-service/PAYMENT-INTEGRATION.md`, `train-service/CATALOG_API.md`, and `schedule-service/SCHEDULE_API.md` for request formats and configuration rules.

## Existing data and migrations

Migrations are supplied for review and manual application. They have **not** been run against your MySQL databases. Stop affected services and back up existing data first; MySQL DDL is not transactional.

- Auth: `auth-service/database/upgrade-existing-auth.sql` adds OTP/account metadata and invalidates old plaintext OTPs.
- Trains: `train-service/src/main/resources/db/manual-migrations/001_catalog_inventory.sql` adds empty operating-day and fare/capacity structures without inventing inventory.
- Schedules: `schedule-service/src/main/resources/db/manual-migrations/001_departure_day.sql` adds departure-day data and route uniqueness checks. Audit legacy midnight stops before enabling booking.
- Reservations: `reservation-service/src/main/resources/db/manual-migrations/001_booking_lifecycle.sql` adds fare snapshots, holds and payment/refund references. Old totals and seats remain preserved; legacy bookings are flagged and excluded from new inventory/payment processing.
- Payments: `payment-service/db/migration/001_archive_original_payments.sql` preserves original rows in a legacy table; `002_create_test_payment_tables.sql` creates the new payment/order/capture/webhook schema. Follow `PAYMENT-INTEGRATION.md`; do not archive an already upgraded database.

Apply the earlier migrations above first if needed, then these additions:

- Auth: `auth-service/database/002_sessions_and_admin_invitations.sql` adds revocable sessions and controlled admin invitations. Existing access tokens require sign-in again.
- Reservations: `reservation-service/src/main/resources/db/manual-migrations/002_waitlist_and_events.sql` adds whole-booking WL and lifecycle notification events. It preserves historical RAC labels in audit columns, maps legacy rows to WAITING for readability, and excludes them from new WL promotion. Historical records remain for review; no payments or seat inventory are fabricated. The old outbox is archived before conversion.
- Users: `user-service/database/001_saved_passengers.sql` adds the passenger Master List.
- Notifications: `notification-service/database/001_notification_inbox.sql` adds durable event deduplication and email delivery retry records.

Scripts are manual, one-time upgrades. Fresh empty demo databases can use the existing Hibernate schema creation setup instead.

The backend supports one train/class per booking, 1–6 passengers, whole-booking CNF/WL allocation and cancellation. Berth preference is recorded; sample seat labels do not model real coach layouts. RAC allocation, per-passenger cancellation and official railway tariffs are outside this implementation. Published routes and configured capacities/operating days are deliberately immutable to protect existing seat allocations; fares and display metadata remain editable.

## Demonstrating the backend

1. Sign in as an administrator and run `Seed-DemoCatalogue.ps1` against your local gateway with its token. It creates/reuses demo station codes and train numbers; it never changes unrelated records or makes bookings/payments. Run it only on a demo database. It does not reset an existing conflicting demo route.
2. For promotion: book and test-pay both seats on DM001, create and test-pay a WL booking, then cancel the CNF booking. Observe automatic promotion, payment/refund statuses and event emails. Use DM002 for ordinary bookings. The script itself has not been run against a live backend here.

Full end-to-end testing, new rate limiting, real payments, RAC, partial passenger cancellation/refunds, date-specific train cancellations and the Angular frontend are outside this backend implementation.

## Verification

From the backend root, run `./Verify-Backend.ps1` for the complete regression suite, or `./Verify-Backend.ps1 -Integration` for the selected integration suites. Both invoke Maven, use isolated H2 databases and controlled external dependencies, and do not load `.env`, alter your MySQL databases, send emails or make provider transactions. Config Server, Eureka, RabbitMQ and provider credentials are not needed for these tests. The HTTP integration suite uses temporary loopback ports, so local port binding must be available.

- `BookingHttpIntegrationTest` starts Reservation Service on a random HTTP port. Real JWT filters, session-verification HTTP calls, controllers, Feign catalogue/PNR clients, booking services and H2 repositories participate. A local HTTP stub substitutes the remote auth, train, schedule and PNR services; the clock is controlled. It covers null passengers, all allowed/rejected berth preferences, station normalization and canonical retries, the two-calendar-month limit (including leap years and India midnight), negative counts, past/departed journeys, paid CNF/WL promotion, cancellation/refund callbacks, expiry, owner/admin access, revoked sessions and PDF downloads. Payment confirmation is supplied through the authenticated internal callback; this suite does not claim to run Razorpay or Payment Service.
- `PaymentFlowIntegrationTest` exercises real payment services, persistence and transactions with mocked provider/reservation boundaries. Its HTTP-layer tests also exercise order creation, verified-payment handling, WL status, duplicate callbacks, ownership and rejection of client-supplied amounts. Provider signature checks have separate tests.
- `StationApiIntegrationTest`, `ScheduleApiIntegrationTest` and `MasterListApiIntegrationTest` exercise the MVC/security/validation/service/repository stack against H2. They cover canonical station creation/lookup/update/uniqueness, timetable publication/replay, identical station detection, saved-passenger preferences and owner-scoped CRUD. These use MockMvc rather than a listening web server; remote timetable catalogue calls are mocked.
- Existing authentication/session/invitation, notification inbox and booking concurrency integration suites are included in `-Integration`. The full run also covers every module's other tests.

These are automated backend/component integration tests. A deployment-wide end-to-end run with real MySQL, RabbitMQ, Google OAuth, SMTP and Razorpay test accounts has not been performed. The validation report records actual suite results and the Windows compiler-cleanup limitation observed inside the restricted agent session.


## Complete local startup and Swagger walkthrough

### 1. Create the eight MySQL databases

Run the following in MySQL Workbench or a MySQL administrator session. These statements create missing schemas only; they do not delete existing data. The MySQL account in DB_USERNAME/DB_PASSWORD must have access to all eight schemas and permission to create/update tables when DDL_AUTO=update.

```sql
CREATE DATABASE IF NOT EXISTS railway_auth_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_user_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_train_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_schedule_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_reservation_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_pnr_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_payment_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_notification_db CHARACTER SET utf8mb4;
```

Each matching business service owns its own schema. Config Server, Eureka and API Gateway need no database. Tables are created by Hibernate in fresh development schemas; do not manually invent tables. If these schemas already contain an older project version, follow **Existing data and migrations** above first.

### 2. Check prerequisites and fill the root .env

Use Java 21+, Maven 3.9+, PowerShell 7, MySQL 8 at localhost:3306 and a running RabbitMQ broker at localhost:5672. Check tools with:

```powershell
java -version
mvn -version
pwsh --version
Set-Location 'C:\Users\sprak\Desktop\railway-reservation-system'
```

The existing root .env is the correct location when using Run-Service.ps1. It is loaded into each service process by that script. Spring Boot/IDE launches do not automatically load this file. Values are plain KEY=value, without surrounding quotes; do not place comments after a value. Do not overwrite the existing file. New-DevEnvironment.ps1 is only for a checkout that has no .env.

| Keys | What to fill |
| --- | --- |
| DB_USERNAME, DB_PASSWORD | Your MySQL login with access to the eight schemas |
| CONFIG_SERVER_PASSWORD, EUREKA_PASSWORD, JWT_SECRET, RAILWAY_INTERNAL_TOKEN | Keep the generated non-empty shared local secrets; JWT/internal values must be at least 32 characters/bytes as validated by the launcher |
| RABBITMQ_ADDRESSES, RABBITMQ_USERNAME, RABBITMQ_PASSWORD | Local broker connection; localhost:5672 and guest/guest are suitable only for a local default broker |
| MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD | SMTP credentials; for Gmail, use an app password with an eligible account |
| GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET | Google web OAuth client credentials |
| GOOGLE_REDIRECT_URI | http://localhost:8080/api/auth/google/callback, also registered exactly in Google Console |
| RAZORPAY_TEST_KEY_ID, RAZORPAY_TEST_KEY_SECRET | Matching test credentials; key ID starts with rzp_test_ |
| RAZORPAY_TEST_WEBHOOK_SECRET | Secret for the test dashboard webhook if configuring webhook delivery |
| BOOTSTRAP_ADMIN_ENABLED | true for initial admin creation, then false |
| BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD | Your chosen initial admin identity and a strong password |
| DDL_AUTO | update for fresh local development databases; review migrations before using existing data |

Keep the supplied localhost URLs and ports unless you deliberately reconfigure the matching services. For local HTTP, SESSION_COOKIE_SECURE=false; secure cookies need HTTPS. Public registration has no role selector. Bootstrap creates the initial administrator; it is not a password-reset mechanism for an existing account.

### 3. Start services in separate terminals

In **each** terminal, change to the project root above, then run the indicated command. Each command stays running; use a new terminal for the next. Wait for Config Server and Eureka to finish starting before business services. Allow discovery registration to settle before using gateway routes.

| Order | Command | Port / purpose |
| ---: | --- | --- |
| 1 | ./Run-Service.ps1 config-server | 8888, central configuration |
| 2 | ./Run-Service.ps1 eureka-server | 8761, service registry |
| 3 | ./Run-Service.ps1 auth-service | 8081, authentication/session checks |
| 4 | ./Run-Service.ps1 user-service | 8082, profiles and saved passengers |
| 5 | ./Run-Service.ps1 train-service | 8083, trains/stations/fares/capacity |
| 6 | ./Run-Service.ps1 schedule-service | 8084, timetables |
| 7 | ./Run-Service.ps1 pnr-service | 8086, identifiers |
| 8 | ./Run-Service.ps1 reservation-service | 8085, booking and promotion |
| 9 | ./Run-Service.ps1 payment-service | 8087, test checkout/refunds |
| 10 | ./Run-Service.ps1 notification-service | 8088, RabbitMQ email consumer |
| 11 | ./Run-Service.ps1 chatbot-service | 8089, optional signed-in Gemini assistant |
| 12 | ./Run-Service.ps1 api-gateway | 8080, single API entry point/Swagger |

After the admin is successfully created, change BOOTSTRAP_ADMIN_ENABLED to false and restart auth-service through Run-Service.ps1. All other services can keep running. To stop a service, press Ctrl+C in its terminal.

Check http://localhost:8761 using EUREKA_USERNAME/EUREKA_PASSWORD and confirm service registrations are UP. Use http://localhost:8080/actuator/health for gateway liveness. Health alone does not prove all downstream services work. Auth, payment and notification do not currently expose Actuator health, so check their OpenAPI endpoints, startup logs and actual flows instead of assuming every port has /actuator/health. Config Server/Eureka infrastructure may require their configured Basic credentials; passenger JWTs are for business APIs.

### 4. Open the shared Swagger explorer

Open **http://localhost:8080/swagger-ui.html** and select a service in the dropdown. Auth, User, Train, Schedule, Reservation, PNR, Payment and Notification each publish an OpenAPI definition. Notification deliberately has no public business operations; it consumes events. Config Server and Eureka are infrastructure, not passenger/admin API collections.

Log in using Auth Service, copy the response accessToken, click **Authorize**, and paste the raw token **without** the word Bearer. Swagger adds the header. This authorization is shared when you switch service definitions in this **same gateway explorer** because every definition uses bearerAuth; authorization survives refreshes during the same gateway run. A new gateway instance clears previously saved Swagger tokens before restoring the UI. An open tab also checks the gateway instance before API requests. This is Swagger UI state, not automatic backend token propagation.

- Login/refresh responses do not automatically replace Swagger's saved token. Paste the new accessToken yourself. Access tokens normally last 15 minutes.
- To switch passenger/admin, click Logout in Swagger's Authorize dialog and enter the other access token. Tokens survive page refreshes during the same gateway run and are shared between service definitions in the gateway explorer.
- Separate direct-service Swagger pages on different ports have different browser origins. Do not expect authorization to carry to those pages.
- A successful POST /api/auth/logout or /api/auth/logout-all executed from the gateway Swagger UI clears its current and persisted bearer token automatically. A failed logout does not clear it. The Authorize dialog's Logout button clears Swagger credentials locally; use the backend logout API to revoke the session too.
- Restart Config Server and then API Gateway to load these settings. Swagger requires a fresh token after each gateway restart, including on the first use after this upgrade. Restarting a downstream service alone does not reset the gateway instance. This resets Swagger browser credentials, not every persisted backend session or other API client. An Authorize button or endpoint padlock alone does not prove a request is authenticated.
- Swagger lifecycle checks: run `node --test src/test/js/swagger-session.test.cjs` from api-gateway (Node.js is needed only for this browser-script test). The gateway Java suite checks the generated initializer, persistence configuration and credential forwarding. Verification on this change: 9 lifecycle tests and 8 gateway Java tests passed; Java compilation used the isolated compiler harness because the normal Maven compiler encounters a Windows archive-cleanup permission error in this execution environment.
- Public catalogue/search/login/OTP operations show no JWT lock. Admin operations are labelled ADMIN; the lock alone does not imply that a passenger can call them.
- Internal service-token endpoints and obsolete POST /api/auth/google are excluded from the explorer. The runtime endpoints/security themselves have not been relaxed.
- Examples are templates. Replace email, OTP, IDs, PNRs, tokens, date and fare with values from your run. The booking example defaults to tomorrow when the document is generated; refresh/reload after a date change.

Expand an operation, click **Try it out**, fill the path/query/header fields and JSON, then **Execute**. Responses show status, body and headers. The next section gives the recommended sequence; the complete body reference follows it.

### 5. Run the functional checks in order

**Authentication and passenger setup**

1. Register demo_user with an email inbox you control; verify the emailed six-digit OTP. Registration returns 201; the OTP is not hardcoded.
2. Log in with identifier (username or email) and password; save accessToken and refreshToken. Authorize as that passenger.
3. PUT /api/users/me, GET /api/users/me, and GET /api/users/demo_user. Before the first PUT, a registered account receives 200 with its username and empty optional profile fields. User-service verifies account existence with auth-service using the configured internal service token. Unknown usernames return 404; unavailable account verification returns 503.
4. POST /api/users/me/passengers, then GET its paginated list. Save the returned passenger ID; use it in PUT and DELETE. A saved passenger is copied into a booking request, not referenced there by its ID. Map berthPreference to booking's existing birthPreference spelling.
5. Test refresh, resend OTP and password recovery on appropriate test accounts. Password reset revokes sessions, so do it after the booking demonstration or log in again.

**Admin catalogue setup**

6. Log in with your bootstrap admin credentials and replace Swagger authorization with the admin accessToken.
7. Create three stations with codes DSTA, DSTB and DSTC. Save their actual numeric stationId values. Create train DM001 with two SL seats and the DSTA/DSTC IDs. IDs 1 and 3 in the example are placeholders, not guaranteed database values.
8. Publish the three-stop timetable for DM001. GET the train, all three station lookups and its schedule to verify them. Catalogue POSTs return 201, schedule publication returns 200. Repeating an existing station/train creation produces a conflict; reuse existing demo records instead. The timetable example matches Seed-DemoCatalogue.ps1, so either use the manual path or the script. Do not change an existing published route/capacity to make a demo fit.
9. Optional regular-booking train: repeat the train body with trainNumber DM002, trainName Demo Regular Express and seatsPerCoach 20; publish the same timetable under /api/schedules/DM002. The seed script creates both demo trains if absent.
10. Test paginated public reads, and optional PUT display-only edits. For a station change stationName only; for a train change trainName only while retaining its established configuration. There are no station/train delete APIs or train-cancellation API in this version.

**Quote, book and make a test payment**

11. Switch back to a passenger token. Choose tomorrow's date in Asia/Kolkata; substitute its YYYY-MM-DD value everywhere. Do not repeatedly use a stale date from an example.
12. GET /api/reservations/search with fromStation=DSTA, toStation=DSTC, dateOfJourney=your date, seatClass=SL, passengers=2. GET /quote with the same values plus trainNumber=DM001.
13. For the unchanged demo fare (200 km, base 20, rate 0.50/km, minimum 40), each passenger costs INR 120 and two cost INR 240. Always use the actual returned totalFare; a changed configuration invalidates an old quote.
14. POST /book with two passengers, that quote total and header Idempotency-Key=swagger-demo-cnf-001. Expect 201, PENDING_PAYMENT, allocation CNF if the run is empty, a PNR and holdExpiresAt. Save the PNR. Retrying the identical body/key returns the same booking; a different body needs a new key.
15. Immediately POST /api/payments/create-order with that PNR. Save keyId, razorpayOrderId, amountInPaise, currency and deadline. Complete Razorpay TEST browser checkout within the two-minute hold; preparing the checkout page in advance helps. No real money is charged.
16. POST /api/payments/verify with the actual signed test checkout callback values. Check GET /api/payments/{pnr}/status and GET /api/reservations/pnr/{pnr}. The paid ticket must be CONFIRMED, not merely an order that was created. A late capture is refunded instead of reviving an expired hold.
17. GET /api/reservations/{pnr}/ticket.pdf to download the demo PDF. It requires captured payment and ownership/admin access; it is not valid for travel.

**Razorpay limitation in Swagger:** Swagger can create the order, verify the callback and read payment/refund state. It cannot render Razorpay Checkout or produce a genuine signature. Use the separate Angular frontend when available, or your own temporary browser Checkout page outside this backend project. The browser loads https://checkout.razorpay.com/v1/checkout.js and calls new Razorpay with the response keyId as key, razorpayOrderId as order_id, amountInPaise as amount, and currency; its handler supplies razorpay_order_id, razorpay_payment_id and razorpay_signature, mapped to the camelCase verify JSON fields below. Never put key-secret or webhook-secret in browser code. Use only test methods offered by your Razorpay test checkout. If there is no Checkout page yet, the successful payment/CNF/WL manual flow cannot be completed using Swagger alone; the automated integration tests cover it with a mocked provider. Fake IDs/signatures must fail.

**Waitlist, promotion, cancellation and emails**

18. With both DM001 seats occupied by the paid two-passenger ticket, quote and book one more passenger for the same date/class/segment with a new Idempotency-Key=swagger-demo-wl-001, expectedTotalFare from the one-passenger quote (normally 120), and a one-element passengers array. Check allocation WL before paying; CNF means your chosen run still had capacity.
19. Test-pay that WL booking too. It becomes WAITING, not CONFIRMED. An unpaid WL checkout has no active queue position. Check its history/status and the WL email.
20. GET the CNF booking's cancellation-preview, then DELETE that CNF PNR. Under unchanged default rules, a paid INR 240 CNF booking retains INR 48 and requests an INR 192 test refund. Check the actual preview/current response. Cancellation is for the entire booking.
21. Poll the WL booking. With no older blocking WL booking, it automatically becomes CONFIRMED and receives seats; the passenger does not pay again or accept promotion. Check the promotion email and download its updated PDF. Processing normally occurs during lifecycle calls or the ten-second scheduler cycle; provider/email processing is asynchronous.
22. Test cancelling a paid WL ticket while seats remain full: its refund is full, with no cancellation fee. Check status until the provider reports the test refund outcome; PENDING is not completed. Do not equate an email with the authoritative booking/refund state.
23. A remaining paid WL ticket is cancelled/refunded at one hour before its own boarding time. This is not the two-minute payment hold. The controlled-clock integration tests cover the deadline without waiting. For a live manual demonstration, arrange an appropriate future timetable/run and leave a paid WL booking waiting through the cutoff; there is no public force-confirm/force-refund or fast-forward endpoint.
24. To check unpaid expiry, create a separate booking and do not pay. After more than two minutes, retrieve its booking state; it should be EXPIRED and its held CNF seats reusable. Do not reuse an already-consumed idempotency key for a new booking.

**Additional services and access checks**

25. PNR Service GET /api/pnr/generate requires authorization and allocates an identifier. It does not create a booking; normally the reservation service calls it automatically.
26. Notification Service is checked by lifecycle emails and its application logs/RabbitMQ consumer, not POST JSON. Auth sends registration/reset/invitation emails itself. The notification consumer handles booking payment, promotion, cancellation and refund events. Watch notification_deliveries for delivery/retry state if diagnosing; do not manually edit lifecycle records.
27. For a second administrator, current ADMIN calls POST /api/auth/admin/invitations. The recipient registers/verifies the invited email, signs in, then submits the emailed token to /api/auth/admin-invitations/accept. The recipient must log in again after acceptance. List/revoke unused invitations through the ADMIN endpoints. This is optional for a single-admin evaluation.
28. Finally POST /api/auth/logout (no body). Retry a protected API with the old token; expect 401. Log in again, then test /logout-all across two separately created sessions. Gateway Swagger clears its saved authorization automatically after a successful logout response.

### 6. Read-only endpoint and filter examples

All paths below are relative to http://localhost:8080. Use Swagger's separate query fields rather than pasting the entire query into one field. Replace PNR/ID/date placeholders.

```text
GET /api/trains?page=0&size=10&sort=trainNumber&direction=asc
GET /api/trains/DM001
GET /api/stations?page=0&size=10&sort=stationName&direction=asc
GET /api/stations/{stationId}
GET /api/stations/code/DSTA
GET /api/schedules/DM001
GET /api/users/me
GET /api/users/demo_user
GET /api/users/me/passengers?page=0&size=10
GET /api/reservations/search?fromStation=DSTA&toStation=DSTC&dateOfJourney=YYYY-MM-DD&seatClass=SL&passengers=2
GET /api/reservations/quote?trainNumber=DM001&fromStation=DSTA&toStation=DSTC&dateOfJourney=YYYY-MM-DD&seatClass=SL&passengers=2
GET /api/reservations/my-bookings?page=0&size=10&sort=bookingTime&direction=desc
GET /api/reservations/my-bookings?page=0&size=10&status=WL&fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD&sort=dateOfJourney&direction=asc
GET /api/reservations/my-bookings?status=CNF
GET /api/reservations/pnr/{pnr}
GET /api/reservations/{pnr}/cancellation-preview
GET /api/reservations/{pnr}/ticket.pdf
GET /api/payments/{pnr}/status
GET /api/pnr/generate
GET /api/auth/admin/invitations?page=0&size=10
```

History filters apply to journey date, not booking creation date. Dates are inclusive and fromDate must be <= toDate. Paginated lists return content, page, size, totalElements and totalPages; request later pages until complete. Valid history sorts: bookingTime/dateOfJourney/trainNumber/status/pnr. Train sorts: trainId/trainNumber/trainName. Station sorts: stationId/stationCode/stationName. Direction is asc or desc. Master List uses name order and accepts page/size only.

### 7. Complete JSON body reference

These are editable templates, also present in Swagger's Example Value. Examples do not override server validation or authorization. IDs and credentials marked REPLACE must come from your run. All request-body APIs exposed in the explorer are covered; GET/DELETE and the query-only POSTs listed afterward need no JSON.

#### Authentication and administration

**POST /api/auth/register** — Use an email inbox you control. Read the emailed OTP and verify before login. No role field is accepted.

```json
{
  "username": "demo_user",
  "email": "your-email@example.com",
  "password": "DemoPass@123"
}
```

**POST /api/auth/verify-otp** — Replace the sample OTP with the six-digit code sent to your inbox.

```json
{
  "email": "your-email@example.com",
  "otp": "123456"
}
```

**POST /api/auth/login** — Copy accessToken from the response into Authorize. Login does not automatically authorize Swagger.

```json
{
  "identifier": "demo_user",
  "password": "DemoPass@123"
}
```

**POST /api/auth/refresh** — Use your actual refreshToken and update Swagger Authorize with the returned accessToken.

```json
{
  "refreshToken": "REPLACE_WITH_YOUR_REFRESH_TOKEN"
}
```

**POST /api/auth/reset-password** — Use the password-reset OTP, not a registration OTP. Existing sessions are revoked; sign in again.

```json
{
  "email": "your-email@example.com",
  "otp": "123456",
  "newPassword": "NewDemoPass@456"
}
```

**POST /api/auth/google/exchange** — Use the code from the frontend callback URL within 60 seconds, in the same browser that started sign-in. It is not the Google authorization code.

```json
{
  "code": "REPLACE_WITH_ONE_TIME_FRONTEND_CALLBACK_CODE"
}
```

**POST /api/auth/admin/invitations** — ADMIN only. The invitation secret is sent by email; it is not returned here.

```json
{
  "email": "invited-person@example.com"
}
```

**POST /api/auth/admin-invitations/accept** — Sign in as the verified invited email address. Acceptance revokes existing sessions; log in again for an ADMIN token.

```json
{
  "token": "REPLACE_WITH_THE_FULL_TOKEN_FROM_YOUR_INVITATION_EMAIL"
}
```

#### Profiles and passenger Master List

**PUT /api/users/me** — The signed-in username is selected by the server.

```json
{
  "firstName": "Demo",
  "lastName": "Traveller",
  "phoneNumber": "9876543210",
  "gender": "OTHER",
  "dateOfBirth": "2000-01-01",
  "address": "Demo address, Mumbai"
}
```

**POST /api/users/me/passengers** — Owner only. Age is 1-120. Preferences are recorded but not guaranteed.

```json
{
  "name": "Demo Traveller",
  "age": 25,
  "gender": "OTHER",
  "berthPreference": "LOWER"
}
```

**PUT /api/users/me/passengers/{id}** — Owner only. Changes do not modify existing booking snapshots.

```json
{
  "name": "Demo Traveller",
  "age": 25,
  "gender": "OTHER",
  "berthPreference": "LOWER"
}
```

#### Station and train catalogue

**POST /api/stations** — ADMIN only. Station codes are trimmed and uppercased.

```json
{
  "stationCode": "DSTA",
  "stationName": "Demo Origin",
  "city": "Demo City",
  "state": "Demo State",
  "zone": "DEMO"
}
```

Also POST these two stations separately, then retain all three returned station IDs:

```json
{"stationCode":"DSTB","stationName":"Demo Junction","city":"Demo City","state":"Demo State","zone":"DEMO"}
```

```json
{"stationCode":"DSTC","stationName":"Demo Destination","city":"Demo City","state":"Demo State","zone":"DEMO"}
```

**PUT /api/stations/{stationId}** — ADMIN only. The established station code is immutable; display details may change.

```json
{
  "stationCode": "DSTA",
  "stationName": "Demo Origin",
  "city": "Demo City",
  "state": "Demo State",
  "zone": "DEMO"
}
```

**POST /api/trains** — ADMIN only. Replace sourceStationId/destinationStationId with actual station IDs. Empty capacity/days cannot be booked.

```json
{
  "trainNumber": "DM001",
  "trainName": "Demo Small Capacity Express",
  "category": "EXPRESS",
  "sourceStationId": 1,
  "destinationStationId": 3,
  "seatClasses": [
    "SL"
  ],
  "operatingDays": [
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
    "SUNDAY"
  ],
  "classConfigurations": [
    {
      "seatClass": "SL",
      "coachCount": 1,
      "seatsPerCoach": 2,
      "baseFare": 20,
      "ratePerKm": 0.5,
      "minimumFare": 40
    }
  ]
}
```

**PUT /api/trains/{trainNumber}** — ADMIN only. Established identity, route endpoints, capacity, classes and operating days are immutable; fares and display metadata may change.

```json
{
  "trainNumber": "DM001",
  "trainName": "Demo Small Capacity Express",
  "category": "EXPRESS",
  "sourceStationId": 1,
  "destinationStationId": 3,
  "seatClasses": [
    "SL"
  ],
  "operatingDays": [
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
    "SUNDAY"
  ],
  "classConfigurations": [
    {
      "seatClass": "SL",
      "coachCount": 1,
      "seatsPerCoach": 2,
      "baseFare": 20,
      "ratePerKm": 0.5,
      "minimumFare": 40
    }
  ]
}
```

#### Train timetables

**POST /api/schedules/{trainNumber}** — ADMIN only. Create the train and stations first. Supply an array of 2-200 stops. Repeating the identical route is safe; changing a published route returns 409.

```json
[
  {
    "stationCode": "DSTA",
    "stopSequence": 1,
    "arrivalTime": "10:00:00",
    "departureTime": "10:05:00",
    "dayNumber": 1,
    "departureDayNumber": 1,
    "distanceKm": 0
  },
  {
    "stationCode": "DSTB",
    "stopSequence": 2,
    "arrivalTime": "11:00:00",
    "departureTime": "11:05:00",
    "dayNumber": 1,
    "departureDayNumber": 1,
    "distanceKm": 100
  },
  {
    "stationCode": "DSTC",
    "stopSequence": 3,
    "arrivalTime": "12:00:00",
    "departureTime": "12:05:00",
    "dayNumber": 1,
    "departureDayNumber": 1,
    "distanceKm": 200
  }
]
```

#### Search, booking and tickets

**POST /api/reservations/book** — Requires Idempotency-Key (8-80 letters, digits, dot, underscore or hyphen). Copy the quote total into expectedTotalFare. Replay the same key and body after a network interruption; use a new key for a new booking. All passengers share CNF/WL allocation.

```json
{
  "trainNumber": "DM001",
  "fromStation": "DSTA",
  "toStation": "DSTC",
  "seatClass": "SL",
  "dateOfJourney": "YYYY-MM-DD",
  "expectedTotalFare": 240,
  "passengers": [
    {
      "name": "Demo Traveller",
      "age": 25,
      "gender": "OTHER",
      "birthPreference": "LOWER"
    },
    {
      "name": "Second Traveller",
      "age": 30,
      "gender": "FEMALE",
      "birthPreference": "NONE"
    }
  ]
}
```

#### Razorpay test payments

**POST /api/payments/create-order** — Booking owner only. Submit only pnr, never amount. Complete Razorpay browser checkout before the two-minute booking deadline.

```json
{
  "pnr": "REPLACE_WITH_BOOKING_PNR"
}
```

**POST /api/payments/verify** — Booking owner only. Copy all three values from the actual Razorpay test checkout callback. Invented payment IDs/signatures cannot confirm a booking.

```json
{
  "razorpayOrderId": "order_REPLACE_FROM_CHECKOUT",
  "razorpayPaymentId": "pay_REPLACE_FROM_CHECKOUT",
  "razorpaySignature": "REPLACE_WITH_ACTUAL_CHECKOUT_SIGNATURE"
}
```


For a one-passenger WL attempt, use the normal booking body with a fresh Idempotency-Key, the one-passenger quote total, and:

```json
{
  "trainNumber": "DM001",
  "fromStation": "DSTA",
  "toStation": "DSTC",
  "seatClass": "SL",
  "dateOfJourney": "YYYY-MM-DD",
  "expectedTotalFare": 120,
  "passengers": [{"name": "Waitlist Traveller", "age": 28, "gender": "OTHER", "birthPreference": "NONE"}]
}
```

Allocation is selected by current inventory, not by a user-supplied status field. Reuse the returned PNR in create-order and perform a separate signed test checkout.

No JSON body is needed for:

```text
POST /api/auth/resend-otp?email=YOUR_EMAIL
POST /api/auth/forgot-password?email=YOUR_EMAIL
POST /api/auth/logout
POST /api/auth/logout-all
DELETE /api/users/me/passengers/{id}
DELETE /api/reservations/{pnr}
DELETE /api/auth/admin/invitations/{id}
```

**Google sign-in manual check:** configure the Google web client/callback, then open http://localhost:8080/api/auth/google/start in a separate tab in the same browser as Swagger. Complete Google consent. The callback redirects to FRONTEND_CALLBACK_URI with a one-time code. Until Angular exists, that page may fail to load; the code is still visible in its address bar. Within 60 seconds copy that code into POST /api/auth/google/exchange in the same browser's gateway Swagger. The browser session cookie must be present. Do not paste Google's provider authorization code or a code from another browser. A successful exchange returns normal login tokens; manually update Authorize. This flow still depends on valid Google configuration, network access and your consent.

**Razorpay webhook:** POST /api/payments/webhook receives provider-generated raw JSON and X-Razorpay-Signature. It has no bearer requirement. Use configured Razorpay TEST dashboard events for a valid test; the exact body and its signature must match. The webhook signing secret differs from the checkout key secret. No generic copy-paste JSON can produce a valid webhook without its matching signature. A deliberately invalid check can submit the body below with no signature and must be rejected; it must never confirm a ticket:

```json
{"event":"payment.captured","payload":{}}
```

Localhost cannot receive Razorpay's external webhook directly. A reachable HTTPS test endpoint is needed for dashboard delivery. The application's reconciler also checks provider status when browser callbacks/webhooks are missing. No tunnel or external endpoint is created by this project setup.

### 8. Negative and boundary checks

Use a new idempotency key for each different booking request. Start with valid demo values so the intended validation is what fails. Expected statuses below describe this project's API contract, not a successful booking.

| Check | Input/change | Expected result |
| --- | --- | --- |
| Same station | fromStation=DSTA, toStation=" dsta " | 400 after normalization |
| Negative passenger count | search or quote passengers=-1 | 400 |
| Empty passenger list | booking passengers=[] | 400 |
| Null passenger | booking passengers=[null] | 400 with fieldErrors; no saved booking |
| Too many passengers | seven valid passenger objects | 400 |
| Invalid age | age=-1 or 121 | 400 |
| Invalid preference | birthPreference="lower" or "WINDOW"; similarly saved berthPreference | 400 |
| Allowed preference | NONE, LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER, omitted or null | Accepted if other fields are valid; not a seat guarantee |
| Past date | yesterday in Asia/Kolkata | 400 |
| Booking-window boundary | today plus two calendar months vs the following day | Boundary permitted if journey exists; next day 400 |
| Departed today | today with boarding time already passed | 400 |
| Normalized codes | " dsta " to " dstc " | Same route as DSTA to DSTC |
| Fare tampering | expectedTotalFare differs from current quote | Conflict; no underpriced booking |
| Key replay | same key + identical request | Same booking, not a second booking |
| Key misuse | same key + different passengers/date/route | 409 |
| Missing key | POST /book without Idempotency-Key | 400 |
| Client amount | create-order with pnr plus amount | 400; amount is server-calculated |
| Missing authentication | protected endpoint without Authorize | 401 |
| Passenger editing catalogue | valid passenger token on train/station/schedule POST | 403 |
| Another passenger's PNR | booking/payment/PDF access | Access denied; no other user's data returned |
| Invalid list request | page=-1, size=101, unknown sort/direction, reversed dates | 400 |
| Unpaid PDF | ticket.pdf before captured test payment | Rejected; no paid ticket produced |
| Invented payment signature | verify with fabricated callback | Rejected; no ticket confirmation |
| Unavailable dependency | stop auth and call a protected resource with a valid unexpired token | Fails closed with 503; restart auth afterward |

For the null-passenger check, replace date/quote values before sending:

```json
{
  "trainNumber": "DM001", "fromStation": "DSTA", "toStation": "DSTC",
  "seatClass": "SL", "dateOfJourney": "YYYY-MM-DD", "expectedTotalFare": 120,
  "passengers": [null]
}
```

### 9. Repeat automated backend checks

Run these from a new terminal at the project root. Tests use isolated H2, local HTTP stubs and mocks; they do not load .env or require live MySQL/RabbitMQ/provider accounts. Do not deliberately export production/provider credentials into the test shell.

```powershell
./Verify-Backend.ps1
# Or run only the selected integration suites:
./Verify-Backend.ps1 -Integration
```

Per-service Maven results are under that module's target/surefire-reports. Stop if a module reports a failure. The full suite includes generated Swagger HTTP checks for all eight business definitions and gateway tests for dropdown configuration, document routing and bearer-header forwarding. These checks do not automate the browser's consent or payment UI; live MySQL/RabbitMQ/SMTP/Google/Razorpay test validation remains the manual flow above. See VALIDATION.md for the latest counts and the restricted-environment compilation method used here.

### Username choice and ticket design (27 September 2026)

All verified active accounts may change their display/login username once at `GET/PUT /api/auth/username`. This lets Google users replace the generated name. Use 4-20 letters, numbers or underscores; choices are normalized to lowercase. Taken names and a second change return 409. An identical retry returns the current result without another change. The frontend asks the user to acknowledge this one-time choice, and Google users with a generated name are offered the account page after sign-in, with a link to continue their original journey.

The original `users.username` remains the immutable cross-service ownership key and JWT subject. `users.public_username` is the editable display/login alias; `username_changed_at` records the successful change. `AuthResponse.username` remains compatible with existing clients, while `displayUsername`, `usernameChangeAllowed` and `usernameSetupSuggested` drive the frontend. This preserves bookings, saved passengers, refunds, existing sessions and idempotent booking retries without a distributed data migration. Password accounts can subsequently sign in with the chosen name or email; Google accounts continue using Google. The old username is reserved against reuse but no longer works as a password-login name after changing it. Profile lookup by username accepts the new alias with the same ownership checks.

Restart auth-service, user-service, reservation-service and Angular. With the existing default `DDL_AUTO=update`, Hibernate adds the two nullable account columns and the unique public-username constraint. Existing accounts keep working without backfilling. For manually managed schemas (`DDL_AUTO=validate/none`), apply these additions once before restarting:

```sql
ALTER TABLE users ADD COLUMN public_username VARCHAR(20) NULL,
  ADD COLUMN username_changed_at DATETIME(6) NULL,
  ADD CONSTRAINT uk_users_public_username UNIQUE (public_username);
```

Swagger example (authorize first): `PUT /api/auth/username` with `{"username":"my_travels"}`. Read `GET /api/auth/username` to see the current name and remaining allowance. Do not change identity claims in a token manually.

Verification: 37 auth-service, 26 user-service and 77 reservation-service Maven tests passed. PDF previews for confirmed, waitlisted and long-name bookings were rendered and visually checked. Angular AOT/template compilation and 11 rule tests passed. The standard Angular CLI build remains blocked by Windows subprocess `spawn EPERM` in this execution environment; the expanded browser suite still needs a normal local run.

### Profile and timetable fixes (26 September 2026)

- `GET /api/users/me` and an authorized `GET /api/users/{username}` return 200 for registered accounts even before profile details have been saved. Empty fields are intentional: signup does not collect a first name, address or date of birth. `PUT /api/users/me` persists those details.
- On a missing profile row, user-service calls the protected auth-service account-existence endpoint using the existing `RAILWAY_INTERNAL_TOKEN` and `AUTH_SERVICE_URL` configuration. Unknown accounts remain 404, unavailable verification returns 503, and reading another user's profile without ADMIN returns 403. Profile reads never create placeholder database rows.
- Timetable route-validation messages are returned in the existing API error envelope. Source arrival and destination departure must remain null; frontend controls now enforce this and insert intermediate stops before the destination.
- Restart auth-service, user-service and schedule-service after these changes. Restart the Angular frontend as well. No database migration or new environment keys are needed.
- Focused verification: standard Maven test runs passed for auth-service (34 tests), user-service (25) and schedule-service (22), including H2 profile lifecycle/access checks and timetable persistence/validation. These are not a live run against your configured MySQL/Google/Razorpay services.

### 10. Common startup/testing problems

| Symptom | Check |
| --- | --- |
| Unknown database / MySQL access denied | All eight schemas exist; DB_USERNAME/DB_PASSWORD and grants are correct; MySQL is running on 3306 |
| Config Server 401 | Launch with Run-Service.ps1 so matching CONFIG_SERVER credentials are inherited |
| Eureka 401 or missing services | Matching EUREKA credentials, successful service startup, then allow registration to settle |
| Swagger failed to load a service definition / gateway 503 | That service and Eureka must be ready; gateway and config-server must have been restarted after Swagger changes |
| Swagger 401 after successful login | Login does not auto-authorize; paste accessToken, without Bearer; refresh/relogin if expired or revoked |
| Swagger 403 | The token lacks the required ADMIN role or you do not own the resource |
| Empty search | Check published timetable, station order, class capacity, operating day, date window and future boarding time |
| Order rejected/expired | The two-minute hold elapsed, credentials are missing, or a non-test key was supplied |
| Google redirect mismatch | Exact web-client redirect URI must be http://localhost:8080/api/auth/google/callback |
| OTP/lifecycle email missing | SMTP/app password and recipient address; for lifecycle messages also check RabbitMQ and notification retries |
| Address already in use | Stop the previous instance in its terminal before starting another instance on the same port |

For implementation locations: each business service's config/OpenApiConfig.java supplies metadata, operation descriptions, access annotations and JSON examples. Gateway dropdown/persistence settings are in config-server/src/main/resources/config-repo/api-gateway.properties. SwaggerDocumentationIntegrationTest in each business service and SwaggerGatewayIntegrationTest in api-gateway verify the generated/routed contracts. OpenAPI READ_ONLY DTO annotations describe response-only fields without changing runtime persistence.

## Railwize email notifications (27 September 2026)

| Email | Trigger and contents |
| --- | --- |
| Welcome | Immediately after the verification transaction commits; also on first verified Google signup. Confirms account activation and links to sign-in. Returning logins do not resend it. |
| Booking confirmation | Verified captured payment confirms a booking. Includes PNR, train number/name, journey date, departure in IST, boarding/destination station codes, class, passenger names, coach/berth labels and fare. The branded PDF ticket is attached. |
| Paid waitlist | Payment succeeds for WL: explicitly identifies the waitlist and cutoff, with a waitlisted PDF. No confirmed seats are claimed. |
| Waitlist promotion | Entire booking moves to CNF; sends allocated seats and an updated confirmed PDF attachment. |
| Payment failure | Payment-service fetches Razorpay's current state and verifies the order/amount before reporting failure. One failure email per booking avoids repeated reconciliation emails. Links to the booking to check status and retry before the original two-minute hold ends. Failed attempts do not prevent a successful retry. |
| Payment incomplete | An unpaid hold expires, including abandoned checkout/browser disconnects. States that no payment was confirmed before expiry, rather than claiming the provider rejected it. Late captures still follow the existing full test-refund flow. |
| Cancellation | Manual whole-booking cancellation or WL cutoff. Lists all affected passengers, cancellation fee, refundable amount and refund status. No partial-passenger cancellation is introduced. |
| Refund requested / delayed | Distinguishes a requested refund or provider problem from completion. Shows the relevant amount and status. |
| Refund processed | Sent after the provider reports a processed refund and reservation accepts the matching payment/amount update. States that the test refund is processed; it does **not** claim actual bank credit. |

### Design and delivery

The requested emails use Railwize forest-green/ivory styling, a bundled logo embedded with a Content-ID, responsive table layouts, plain-text alternatives and escaped passenger/account text. No remote image download is needed to display the logo. Booking status is captured at event creation: cancellations or promotions that occur while delivery is delayed cannot rewrite an earlier event's passenger/seat details. The email links always open the booking page for its current state.

Reservation outbox events go through RabbitMQ to notification-service's durable inbox. Snapshot details and the attached PDF are retained for inbox retries. The PDF is produced by the same TicketPdfService used for downloads; a rendering failure leaves the confirmation pending for retry. SMTP delivery is retried with the existing five-attempt limit. Welcome delivery is attempted after commit and never reverses a successful verification if SMTP fails; it is not a durable welcome outbox. As with ordinary SMTP, an acknowledgement loss can still produce a duplicate email.

Payment-failure detection uses verified provider data, not a browser's claim. A browser that simply closes is detected when the hold expires. Email transport may outlast a short hold: the backend always rechecks the deadline before accepting payment. Refund reconciliation normally runs approximately every 30 seconds while services are running; external delays can take longer. All payments remain Razorpay TEST; **there is no real charge, bank credit or real banking refund timeline**.

### Configuration and restart

Existing MAIL_HOST, MAIL_PORT, MAIL_USERNAME and MAIL_PASSWORD remain in the root .env. FRONTEND_BASE_URL=http://localhost:4200 is added for email buttons. Use an address reachable by recipients when hosting the frontend. Optional MAIL_FROM overrides the sender address; otherwise MAIL_USERNAME is used, with display name Railwize. No mail provider credentials are included in templates or the frontend.

Restart config-server, auth-service, reservation-service, payment-service and notification-service after this update. Keep RabbitMQ running. With the default DDL_AUTO=update, Hibernate adds the nullable snapshot/name columns. No new database is required. If using DDL_AUTO=validate/none, apply these once to the matching databases:

```sql
-- railway_reservation_db
ALTER TABLE reservations ADD COLUMN train_name VARCHAR(255) NULL;
ALTER TABLE booking_notification_outbox ADD COLUMN booking_json LONGTEXT NULL;
-- railway_notification_db
ALTER TABLE notification_deliveries ADD COLUMN payload_json LONGTEXT NULL;
```

New events include full details and PDF attachments. Old already-queued events without a snapshot retain their available legacy details; old sent emails are not re-sent. Train names are saved for new bookings; older bookings may show only the train number.

### Manual checks with configured services

1. Register and verify once: receive one welcome; rejected/expired OTPs must not send it. First Google signup also sends it, subsequent logins do not.
2. Pay a CNF booking in Razorpay test checkout: inspect PNR, IST time, stations, fare, passenger/seat labels and open the attached PDF.
3. Pay a WL booking: verify the email and attachment say WL. Free enough seats before the cutoff: receive the promotion email and updated confirmed ticket.
4. Cause a test payment failure: receive a failure email; retry before expiry. Reconciliation must not send duplicate failure emails or downgrade a paid booking.
5. Abandon checkout until the hold expires: receive a payment-incomplete notice. An expired retry must be rejected.
6. Cancel CNF: verify all passengers, fee and refund amount. Cancel WL or let its cutoff pass: verify a full eligible refund. Unpaid cancellation has no refund due.
7. Wait for Razorpay's test refund status: receive processed confirmation only after processing, never merely because the refund was requested.
8. Temporarily unavailable SMTP must leave lifecycle email pending for retry, including its PDF. A welcome-mail failure must not undo verified account activation.

### Account email design (28 September 2026)

All existing account mail now uses the same Railwize logo, green/ivory card layout, plain-text fallback and embedded image as the lifecycle emails:

- Registration and resend OTP: prominent six-digit code (including leading zeroes), five-minute validity, single-use explanation, verification steps and a link to the verification page. Opening an email does not verify an account; the OTP must still be submitted.
- Password-reset OTP: separate purpose label, reset steps, five-minute validity, an ignore-if-unrequested notice, and an explicit statement that the password has not yet changed. Passwords and codes are not placed in link URLs or subjects.
- Administrator invitation: invited address, exact expiry in IST, 24-hour/single-use terms, permissions overview, account-verification steps and the existing token-fragment acceptance link. Sending the email does not assign the ADMIN role. Wrong-account, expired and revoked invitation checks and session revocation after acceptance are unchanged.

If SMTP fails during an OTP request or admin invitation, the operation still reports failure and its transaction rolls back. Welcome mail remains an after-commit attempt. `ADMIN_INVITATION_URI` is now explicitly mapped in auth-service configuration, defaulting to `FRONTEND_BASE_URL` plus `/admin/accept-invitation`. Restart config-server and auth-service after this update.

Implementation: `auth-service/.../service/AccountEmailTemplate.java` renders OTP and invitation content; `RailwizeMail.java` provides the common visual shell; `OtpEmailService.java` and `AdminInvitationService.java` send multipart messages. `notification-service/.../service/BookingEmailTemplate.java` renders booking/payment/refund content. The two services bundle their own `src/main/resources/mail/railwize-logo.png` so they can be deployed independently.

Rendered HTML previews are generated by tests in each service's `target/email-preview` directory. These contain synthetic sample data and invalid example invitation tokens. Tests check MIME alternatives/logo/attachments, escaped text, OTP leading zeroes and purpose separation, invitation rollback and existing authorization rules. Visual checks use browser previews; rendering and delivery in your actual Gmail/Outlook mailbox still need the configured SMTP smoke test.

Verification for this email update: 176 tests pass across auth-service (47), reservation-service (75), payment-service (42), and notification-service (12). The affected account-email suites were rerun after correcting MIME test fixtures. Browser previews were inspected at desktop and mobile widths. Tests use mocked mail/provider integrations; live SMTP delivery and Razorpay test checkout still require the manual checks above.


## In-app notification centre (28 September 2026)

The main backend and separate Angular application now include a persistent private notification inbox. The navbar bell shows the unread count and three latest updates; `/notifications` provides paginated All/Unread history, mark-one/mark-all read, and links to the current booking. The panel supports keyboard navigation, Escape/outside-click dismissal, and a mobile layout.

Events: booking/payment hold created; payment confirmed as CNF or paid WL; WL promoted to CNF; provider-reported payment failure; unpaid hold expired; booking cancellation; WL cutoff cancellation; test refund initiated, processed or delayed. Retry payment from the booking page, which rechecks the current hold before allowing checkout. Notifications are historical updates, not proof of the current booking state. Existing payment verification and test-only money rules are unchanged.

Refresh is every **5 seconds** while signed in and the browser tab is visible, with an immediate fetch after successful booking/payment/cancellation actions, opening the bell, or returning to the tab. Failed polling backs off up to 30 seconds; requests time out after 8 seconds and do not overlap. Logout/account changes clear the client inbox and discard stale responses. Provider or scheduled updates appear after the backend records them, normally within the next five-second poll; this is polling, not guaranteed instant push. The existing hold-expiry job and payment/refund reconciliation schedules still apply.

Notifications are inserted in the reservation transaction and deduplicated by PNR/event type. They do not wait for RabbitMQ or SMTP. Read timestamps are stored in a separate `user_notifications` table, so email retries cannot overwrite them. The immutable JWT subject determines ownership; administrators can only view their own inbox, not another user's. Changing a display username preserves notification ownership. No passenger details, email addresses, ticket PDF payloads or provider credentials are returned by this API. History survives logout and restart; only newly occurring events are added, with no bulk backfill of old email records.

### Start and verify

- Backend changed in `C:\Users\sprak\Desktop\railway-reservation-system\reservation-service`.
- Main Angular source changed in `C:\Users\sprak\Documents\Codex\2026-09-16\i\work\railway-reservation-frontend` (kept separate from the backend).
- Restart reservation-service and Angular. No new environment keys or database are required. With the default `DDL_AUTO=update`, Hibernate creates `user_notifications`. For `validate/none`, apply `reservation-service/src/main/resources/db/manual-migrations/003_in_app_notifications.sql` once to `railway_reservation_db` first.
- Swagger: choose Reservation Service and the **Notifications** tag. Authenticate normally. All endpoints also route through the existing gateway reservation route.

| Method | Endpoint | Behaviour |
|---|---|---|
| GET | `/api/reservations/notifications?page=0&size=20&unreadOnly=false` | Newest first, page size 1-100, returns `notifications` page plus total `unreadCount` |
| PUT | `/api/reservations/notifications/{id}/read` | No body needed; 204; idempotently marks an owned item read; missing/other-account IDs return 404 |
| PUT | `/api/reservations/notifications/read-all` | No body needed; 204; marks only the current account's existing entries read |

Use the `id` from the list response, URL-encoded where necessary. API ownership comes from the token, not a supplied username.

Manual checks: create a booking and watch the bell update; complete test checkout; trigger a WL promotion/cancellation/refund; click one notification; mark all read; filter unread; reload and sign in again to check persisted read state. A different account must see only its own updates. Notifications should stop polling on logout/hidden tabs and resume on return.

Verification: all 78 reservation-service tests passed, including real-HTTP inbox authentication/ownership (passenger and admin), pagination, invalid inputs, persistent/idempotent reads, new unread events after read-all, event deduplication and transactional rollback. All 17 existing frontend rule tests passed; strict Angular template/TypeScript compilation and the linked Angular verification bundle passed. Desktop/mobile browser checks with synthetic API fixtures verified new-event polling, read actions, filtering, error recovery and booking navigation. Two repeatable Playwright notification scenarios were added. The standard production build and Playwright worker cannot start subprocesses here (`spawn EPERM`); run `npm run build` and `npm run test:e2e` locally. The new bell should also be checked against your running backend; previous SMTP and Razorpay tests are unaffected.

## Railwize AI assistant (28 September 2026)

The original backend now contains `chatbot-service`; the existing, separate Angular application has `/assistant` and an **Ask Railwize** launcher for signed-in passengers and administrators. It uses the **Gemini Developer API through Google AI Studio**, not Vertex AI. No Docker, WSL, Redis, vector server, new SQL database, or Google Cloud service-account file is needed. The chatbot still needs internet access for Gemini and the existing local services for authentication and live booking data. Existing application databases/RabbitMQ remain part of the rest of Railwize.

### Setup and launch

1. In the root `.env`, fill `GEMINI_API_KEY` with your AI Studio Developer API key. Keep it server-side; never add it to Angular or paste it into chat. Existing `.env` values were preserved.
2. Defaults are `GEMINI_CHAT_MODEL=gemini-3.5-flash-lite`, `GEMINI_EMBEDDING_MODEL=gemini-embedding-001`, and `CHATBOT_PORT=8089`. Check model availability and the free quota for your own AI Studio project. The service never switches to another provider or enables billing, but the key's project/tier determines Google's billing; use your free-tier project as intended. Both chat and embedding requests consume quota.
3. Restart Config Server, reservation-service (new owner-only lookup), and API Gateway to load the new configuration. Keep Eureka, auth-service, train-service, schedule-service, and the normal reservation dependencies running.
4. From a PowerShell terminal at `C:\Users\sprak\Desktop\railway-reservation-system`, run `./Run-Service.ps1 chatbot-service`. The script loads the root `.env`. For IntelliJ, supply those same environment variables to the chatbot run configuration; Spring does not automatically read `.env`.
5. Restart Angular with `npm start` in `C:\Users\sprak\Documents\Codex\2026-09-16\i\work\railway-reservation-frontend`. Sign in and open `http://localhost:4200/assistant`, or use **Ask Railwize**.
6. Gateway Swagger now includes **Chatbot Service**. Authorize with a normal access token. `GET /api/chat/status` shows `configured`, `ragReady`, memory limits and circuit state. `configured=true` means a key was provided, not that Google accepted it. `ragReady=true` means FAQ indexing succeeded. Indexing starts asynchronously; a failed attempt is retried after one minute. A missing/invalid key, provider outage or exhausted quota must not stop ordinary bookings.

For a portable build, run `mvn clean package` inside `chatbot-service`, then run its JAR with Java 21+ and the required environment variables. No chatbot state files or containers need to be carried between machines. Override `AUTH_SERVICE_URL`, `TRAIN_SERVICE_URL`, and `RESERVATION_SERVICE_URL` if those services are not at their default localhost ports (8081, 8083, 8085). Gateway discovery uses the existing Eureka configuration.

### What is implemented

- **RAG:** Spring AI `SimpleVectorStore` in memory, Gemini embeddings with 768 dimensions, and eight bundled FAQ sections at `chatbot-service/src/main/resources/knowledge/railwize-faq.md`. Retrieval returns up to three relevant policy references. Update this file when configurable refund/hold/WL rules change, then restart chatbot-service to rebuild vectors and clear old cached answers. These are Railwize's application rules, not external railway policies.
- **Read-only live tools:** public station lookup, current train availability/total fare, and the signed-in user's booking/refund status. Backend Java methods call existing services with `WebClient`; JWTs are forwarded locally. The model cannot book, cancel, pay, refund, change roles, or invoke arbitrary URLs/methods. A verified train card opens the existing booking form, where price and availability are rechecked. Station-name lookup is bounded to the first 300 catalogue entries; exact station codes work for larger catalogues.
- **Private PNR lookup:** enter `PNR 1234567890`. The service replaces that number with a conversation-local alias before calling Gemini. Its owner-only reservation endpoint (`GET /api/reservations/my-bookings/{pnr}`) disallows cross-account access even for ADMIN. Raw PNR, passenger names, email and private booking-card details are kept out of model tool responses. The model receives the alias and status; the authenticated browser receives the verified card.
- **Memory:** `MessageWindowChatMemory` backed by `InMemoryChatMemoryRepository`, up to 20 messages. Conversations belong to the JWT subject and expire after 24 hours without a completed exchange. Browser refresh can reopen the current conversation; restarting chatbot-service clears all conversations, aliases, vectors and caches. New conversation clears the previous one. The browser stores only the conversation ID in addition to its existing auth session. A maximum of five conversations per account and 500 total bounds memory usage.
- **Semantic cache:** at most 128 policy answers, 30-minute TTL, cosine similarity of at least 0.96 plus matching policy qualifiers. Only eligible first-turn generic FAQ questions with retrieved references are cached. Private, contextual and live-tool answers are excluded. Exact cached answers remain available while the provider circuit is open. Similarity is conservative but cannot prove semantic equivalence; policy citations remain visible.
- **Streaming and structured output:** authenticated POST over WebFlux SSE. `BeanOutputConverter<AnswerChunk>` supplies the schema; complete JSON paragraph records are decoded, validated and filtered before display. The UI streams short paragraphs rather than exposing raw unchecked tokens/JSON. Cards are typed server records made from backend results, never model-invented prices or booking states. Malformed output, interrupted responses and cancellation are handled; partially received replies are not automatically replayed.
- **Guardrails:** input length, common prompt-injection checks, email/secret/long-number masking, output checks and no prompt-content logging. These are defence in depth, not a guarantee that every possible PII form or injection can be recognized. Never enter passwords, OTPs or payment details. Authentication, ownership checks and a fixed read-only tool list enforce the actual permissions independently of model instructions. Redacted messages are still sent to Google; this is not an offline/local LLM.
- **Resilience:** Resilience4j circuit breaker opens after sufficient provider failures, waits 30 seconds and permits one probe. Provider automatic retries are disabled; per-request tool/model rounds and concurrency are bounded. Idle timeout is 25 seconds, absolute response deadline 55 seconds, gateway response timeout 65 seconds. Static bundled FAQ guidance is used when AI cannot answer. There is no paid fallback. Stopping the browser stream cancels local work; a request already accepted by Google may still count toward quota.

### API flow and manual checks

All chat operations except public documentation/health require a valid JWT and an active auth-service session. Logout/revocation therefore prevents new calls. The request accepts no username or role override.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/chat/status` | Configuration/index/circuit readiness |
| POST | `/api/chat/conversations` | Create; no body; returns `id`, expiry and messages |
| GET | `/api/chat/conversations/{id}` | Read my recent messages/cards |
| DELETE | `/api/chat/conversations/{id}` | Clear my conversation; returns 204 |
| POST | `/api/chat/conversations/{id}/messages` | Body `{"message":"What is the cancellation refund policy?"}`; SSE stream |

SSE event types are `status`, `delta`, `cards`, `sources`, `fallback`, `done`. Each `data` JSON object has `type`, `text`, `cards`, `sources`. A fallback replaces partial answer text. `done` identifies complete/cached/fallback completion. Use the Angular page to see progressive rendering; Swagger is useful for contracts and the non-streaming endpoints.

Check these with your configured Gemini key and running services:

1. Ask about cancellation, WL promotion, the two-minute hold and the two-month booking window; inspect policy references. The answer must not invent real payments or RAC.
2. Ask for trains using real station codes, journey date, class and passenger count. Follow **Book now** and check the existing form receives these details. A train option shows available seats, not a claim that a ticket has been booked.
3. Enter one of your own PNRs; compare status/refund card with My journeys. Try a PNR owned by a different account, including while signed in as admin: no private booking card should be returned.
4. Ask a follow-up, refresh, start a new conversation, and restart chatbot-service. Verify the documented memory/reset behaviour. A different account cannot read/delete the original conversation ID.
5. Test an expired/revoked JWT, blank/oversized message, unavailable auth service, missing API key, Gemini quota failure, malformed provider response, and Stop during a reply. Ordinary booking screens must remain usable.
6. Ask the same general FAQ question in a new conversation to exercise cache reuse. Live availability/PNR results must always be fetched again.

Verification for this change: **20 chatbot tests**, **31 reservation HTTP integration tests**, and **20 frontend unit checks** pass. Chatbot coverage includes real HTTP authentication/session checks, ownership (including admins), SSE fallback/validation/Swagger, real SimpleVectorStore indexing/retrieval with deterministic test embeddings, memory expiry, PII aliases, structured decoding, cache isolation, circuit-open cache reads, a repeated tool loop stopped before its fifth model call, and local HTTP tools with JWT forwarding. Reservation tests use H2 and local dependency stubs. No live Gemini API calls or free quota were consumed by these tests. Strict Angular compilation and a linked browser verification build are also checked. The regular `npm run build` is blocked in this tool environment by `spawn EPERM`; run it on your local terminal before packaging. A real Gemini conversation and all-services smoke test remain necessary after adding the key.


The standalone executable `chatbot-service/target/chatbot-service-0.0.1-SNAPSHOT.jar` was packaged successfully. Browser checks using synthetic local API fixtures verified progressive replies, available-seat labels, booking query parameters, new-conversation reset, responsive layouts and the logged-out sign-in redirect. These fixture checks do not replace the live Gemini/all-services checklist above.

## Final issue fixes (29 September 2026)

- **Account-specific recent searches:** the Angular app keys saved searches by the immutable account subject, resets the displayed list when the account changes, and discards the old shared list because it has no known owner. Guest searches are memory-only. Changing a display username does not lose account history. Browser storage is a convenience, not protection against someone inspecting the browser's local files.
- **Booking emails:** inspected logs showed RabbitMQ connection refusal while auth-service SMTP continued working. Reservation's durable outbox now attempts an authenticated HTTP handoff to notification-service if RabbitMQ publication fails. The receiver commits the event to its durable inbox before acknowledging it; the same event ID deduplicates HTTP/broker redelivery. Existing confirmation/promotion PDF attachments, payment-failure, cancellation and refund templates are preserved. Events remain pending if both transports fail. Undelivered reservation events become eligible for retry on startup, and mail handoff runs independently of the hold-expiry scheduler.
- **Gemini:** the configured `gemini-2.5-flash-lite` returned HTTP 404 for this API project. The default and root `.env` now use `gemini-3.5-flash-lite`; embedding stays `gemini-embedding-001`. Google's [pricing page](https://ai.google.dev/gemini-api/docs/pricing) lists a free tier for this model; availability and quotas still depend on your AI Studio project. No billing/provider change was made. Actual Spring AI calls verified FAQ embedding/retrieval, streamed replies, and function calling against a synthetic local train catalogue.
- **Related chatbot fixes:** the circuit breaker can recover after its open interval; formatted/chunked JSON replies decode correctly; AC class codes and aliases work in train tools; unavailable embeddings fall back to bundled FAQ title matching without automatically disabling chat. Authenticated `/api/chat/status` now includes a safe `lastFailure` code such as `MODEL_UNAVAILABLE`, `API_KEY_REJECTED`, `RATE_LIMITED`, `TIMEOUT` or `CIRCUIT_OPEN`. `NONE` after a successful reply does not guarantee future quota availability. Provider payloads, keys and prompts are not logged.
- **Compact UI:** navbar notifications are 340px wide with a scrollable three-item preview; all history stays on `/notifications`. The assistant has a narrower panel and a 300px conversation viewport (responsive on mobile). Five-second notification refresh is unchanged.
- **Profile email:** `/api/users/me` returns the verified JWT email for both passenger and admin profiles. Angular shows it read-only under Personal details. A supplied profile-update email cannot replace the authenticated email.

### Restart and verification

Restart Config Server, user-service, notification-service, reservation-service, chatbot-service and Angular. Use `Run-Service.ps1` so each service gets the root `.env`; IntelliJ run configurations must supply the same values explicitly. Rebuilt executable JARs are in Config Server and the four changed application services' `target` folders.

The email fallback uses the existing shared `RAILWAY_INTERNAL_TOKEN` (at least 32 characters) in both reservation-service and notification-service. Optional `NOTIFICATION_SERVICE_URL` defaults to `http://localhost:8088`; set it only if that service runs elsewhere. The internal POST `/internal/notifications/events` is token-protected, hidden from Swagger and not routed through API Gateway. Keep internal service ports on a trusted local/private network. RabbitMQ is still the primary delivery route. Handoff success means queued, not SMTP delivery; notification-service still retries failed mail delivery up to five attempts.

After RabbitMQ was restarted, its local TCP listener on port 5672 was verified reachable. This connectivity check does not prove authentication, queue consumption or external SMTP delivery; use the mailbox check below to verify the complete running system.

After restart, allow the outbox/inbox scan intervals to run. Check one paid booking (confirmation with PDF), its cancellation and final processed refund in the mailbox. Existing undelivered outbox events retry too; already delivered events are not bulk resent. Check the Spam folder if necessary. A pending refund produces a different email from a provider-confirmed completed refund; all money remains test-mode only.

Latest automated verification: 86 reservation tests, 14 notification tests, 27 user-service tests and 23 offline chatbot tests passed, plus the explicitly opted-in live Gemini smoke test. Coverage includes broker-to-HTTP fallback, both transports unavailable, real HTTP inbox authentication/deduplication, mail MIME/PDF content, read-only profile email for both roles, circuit recovery and AC class aliases. Mail sending is mocked in automated tests; these results do not claim delivery to an external mailbox. Live Gemini checks send synthetic questions/data only and consume a small amount of free quota. They do not run in ordinary Maven tests (`-Drailwize.live-gemini=true` is required).

Frontend verification: 21 unit checks, strict Angular AOT compilation and a linked browser bundle passed. Browser fixture checks verified the email field, compact desktop/mobile panel bounds and chat rendering. The standard Angular production build/Playwright worker remains blocked here by Windows subprocess `spawn EPERM`; run `npm run build` and `npm run test:e2e` in your normal terminal before final packaging. Earlier dated verification entries above describe their respective versions; this section supersedes their model and live-Gemini limitations.

Final browser account-switch check: an Alice search disappeared after logout, Bob saw no recent searches, and signing back into Alice restored only Alice's saved route. The frontend production build was attempted again and reported the same Windows spawn EPERM restriction.
## Connecting journeys and nearby stations (30 September 2026)

Railwize now finds alternatives when the exact station pair has no suitable direct train. The Angular search page automatically loads alternatives when no direct result has CNF availability; **Find alternatives** also works alongside available direct trains. Direct search and booking APIs remain compatible.

- **One interchange:** two different trains, changing at the same station. Each leg must follow its timetable's stop order, operate on its original run date, depart in the future and have a boarding date within the two-calendar-month booking window. Overnight arrivals/departures and intermediate boarding on day 2+ are supported. Routes that double back through an already visited station are excluded.
- **Transfer time:** defaults to 60-360 minutes. The UI offers minimums of 60, 90 or 120 minutes. API minimum is 30-240 minutes, maximum is at least the minimum and at most 720 minutes. These are scheduled transfer buffers, not a prediction of delays or a guaranteed connection.
- **Nearby stations at either end:** optional latitude/longitude on catalogue stations; Haversine straight-line distance selects candidates within 25/50/100 km in the UI (50 km default; API accepts 1-100). Exact stations plus up to four nearest alternatives on each side are searched. A direct alternative can change the boarding station, arrival station or both; nearby endpoints also work with connecting trains. Missing coordinates produce a notice and exact-station connections still work. City names are not treated as geographic coordinates.
- **Fare and availability:** each leg uses the existing validated fare calculation and segment inventory. The total is the sum of train fares for all requested passengers; local transport is extra. A WL leg labels the itinerary as including waitlisting. Unavailable legs are excluded. CNF-available itineraries rank first, then earliest final arrival, fare and a stable tie-breaker. Search does not hold inventory; checkout rechecks each leg.
- **Booking:** review the actual stations, transfer and separate-ticket terms, then book each leg using the existing flow. Each leg has a separate PNR, payment, cancellation and refund. Buying one leg does not reserve or guarantee the other. Links preserve the second leg's actual boarding date. **Return to journey options** on the booking/ticket pages restores the original search to find the onward leg. No composite ticket, automatic rebooking, through-payment or cross-station transfer is claimed.

### Enable and test

1. Restart **train-service**, **reservation-service** and Angular. With default `DDL_AUTO=update`, Hibernate adds nullable `latitude` and `longitude` to `railway_train_db.stations`. With validate/none, apply `train-service/src/main/resources/db/manual-migrations/002_station_coordinates.sql` once before starting train-service. Existing stations and bookings remain valid.
2. In **Admin → Stations → Edit**, enter both verified station coordinates or leave both empty. Latitude must be -90 to 90; longitude -180 to 180. Use the station location, not a city centre. These values are optional for ordinary search and exact-station connections, but required for geographic alternatives. No location data was guessed or written into your live database.
3. Create/publish two bookable trains: for example synthetic station `AAA → XXX`, departure 10:00 and arrival 12:00; `XXX → CCC`, departure 13:00 and arrival 15:00, both day 1. Configure the requested class/fare/capacity and matching operating days. Search `AAA → CCC` on an eligible future date: the planner should show a 60-minute transfer at XXX.
4. To test nearby endpoints with synthetic data, give AAA coordinates `(19,73)` and another origin AAN `(19.1,73)`; CCC `(15,74)` and another destination CCN `(15.1,74)`. Each pair is roughly 11.1 km apart. A published AAN→CCN service should appear when searching AAA→CCC with nearby stations enabled. These coordinates/codes are test fixtures, not claims about real stations.
5. Verify overnight transfers, a 59-minute connection being excluded at the default minimum, days the onward train does not operate, missing coordinates, WL on either leg, both endpoints changing, and a second leg outside the advance-booking window. Review the actual station/date passed to checkout. Completing leg 1 must not imply leg 2 is booked.

Swagger: **Reservation Service → Journey search → GET `/api/reservations/alternatives`**. This is public, uses query parameters and has no JSON body:

```text
fromStation=AAA
toStation=CCC
dateOfJourney=<an eligible future YYYY-MM-DD>
seatClass=SL
passengers=2
nearby=true
radiusKm=50
minTransferMinutes=60
maxTransferMinutes=360
```

Station POST/PUT now additionally accepts `"latitude":19.1,"longitude":73.2`; include the existing required station code/name fields. Sending one coordinate without the other or an out-of-range value returns 400. GET station/list responses include the coordinates.

### Implementation and limits

`reservation-service/.../service/JourneyPlanner.java` indexes published routes by departure station, joins feasible legs at a common interchange and uses request-local timetable/quote reuse. `JourneyService.quoteRoute` shares the normal pricing/timetable rules; `BookingEngine.describeAvailability` supplies actual segment availability. `JourneyPlannerController` exposes the planner. Train-service Station/StationDto/StationService store coordinates. Angular `shared/journey-alternatives.ts` renders the controls, itinerary cards and leg links; `core/journey-link.ts` validates only public search fields for return navigation.

To keep this standalone project bounded, each request examines at most 200 catalogue trains and 10,000 stations; at most 5,000 unique leg quotes, 20,000 scan steps and 200 candidate itineraries are evaluated, with a 12-second work budget checked between operations. A downstream request can still run until its configured HTTP timeout. The best 20 evaluated options are returned. `limited` and `notices` explicitly report caps/partial results; the planner does not claim an exhaustive network-wide optimum. Timetables are fetched once per examined train per request; no paid maps, routing API, Redis or new service is required.

Verification: **98 reservation-service tests and 32 train-service tests passed**, including 12 planner/API tests and coordinate persistence/validation. Planner API tests use Spring MVC, real fare/inventory services and H2 with mocked catalogue clients. **23 frontend unit checks**, strict Angular AOT/template compilation and the linked verification bundle passed. Browser checks against synthetic API fixtures verified desktop and 390px mobile layouts without horizontal overflow, review acknowledgement and the correct next-day second-leg booking link. This is not a live two-payment checkout test. A browser regression scenario is in `tests/browser/planner.spec.ts`; the full Playwright worker and standard production build remain subject to this environment's Windows `spawn EPERM` restriction. Run `npm run build` and `npm run test:e2e` locally before deployment, and verify with your own published catalogue.


## Assistant account queries and chat panel (1 October 2026)

- **No PNR needed for account questions:** try `Give my booked tickets`, `Which tickets I have cancelled?`, `How many bookings I have?`, and `When is my next journey?`. Common phrases use an authenticated deterministic shortcut without a Gemini/embedding request. Other phrasing can use the model's new `myBookings` tool. This is read-only and never books/cancels/refunds automatically.
- **Lists and counts:** history is paginated in groups of 8; ask `Show my cancelled bookings page 2` for another page. General history includes all statuses and says so. Counts are PNR/booking records, not passengers, with confirmed, waiting, cancelled, unpaid and expired counts identified separately. Failed API calls are not reported as empty histories.
- **Next journey:** earliest future departure among CONFIRMED and WAITING bookings. Departed, unpaid, expired and cancelled records are excluded. Waitlisted results explicitly say travel is not confirmed; the card displays the actual departure time in IST. Legacy records without a departure timestamp cannot be selected as the next journey.
- **Owner-only API:** `GET /api/reservations/my-bookings/overview` provides `totalBookings`, `byStatus` and `nextJourney` for `Authentication.getName()`. ADMIN has no cross-account privileges on this endpoint. Existing history/detail endpoints supply cards. Passenger names, PNRs, routes, dates, fares and seat assignments from booking responses are not sent to Gemini; the model sees only aggregate notices/status while cards go to the signed-in browser.
- **Accurate failure messages:** all AI errors previously used a fallback mentioning exhausted free quota. Failures now distinguish timeouts, rejected keys, missing configuration, unavailable models, provider rate limiting and an open circuit. HTTP 429 maps to `RATE_LIMITED`; it does not prove which request/token/daily quota was reached. Query embedding failures do not set the chat failure code. Check `/api/chat/status` (authenticated) and Google AI Studio for the actual provider usage. No retry loop or paid service was added. Provider limits: https://ai.google.dev/gemini-api/docs/rate-limits
- **Frontend:** Ask Railwize opens a non-modal right-side panel on the current page. The corner expand link opens `/assistant` (an Angular page, not an API). Both views share one owner-scoped conversation and in-progress reply. Closing the panel leaves the conversation available; logout/account changes clear visible state and abort the old request. The full page has larger typography and content width. Styling changes are confined to the assistant.
- **Train-card booking:** Book now first opens a required 1-6 passenger selector with no implicit choice. Continue passes the selected count to the normal booking page, which requotes fare and availability. Card price remains labelled as the original quote for its original passenger count.

Restart **reservation-service**, **chatbot-service** and Angular. No database migration, new environment variable or API key is required. Existing single-PNR lookup remains available.

Validation: 99 reservation tests passed; chatbot suite passed 29 tests with its opt-in live Gemini test skipped; 25 frontend unit checks passed. Angular strict AOT/templates and the linked verification bundle passed. Browser checks with synthetic API fixtures verified opening/closing the panel without leaving the current page, conversation transfer to the full page, explicit passenger selection and `passengers=3` navigation, plus mobile panel bounds. Standard Angular production build was attempted but blocked by Windows `spawn EPERM`; run `npm run build` locally. No live Gemini quota exhaustion, real account data or payment was exercised. Regression browser scenario: `tests/browser/assistant-panel.spec.ts` in the frontend.

## Coach names and berth descriptions (1 October 2026)

Passenger responses now include server-derived `seatDetails` (coach, position, berthType, berthLabel, description, layout and bay). The Angular ticket page displays a coach badge, berth/seat number and full position label; PDFs, new booking/promotion emails and new confirmation notifications use the same backend interpretation.

| Class | Coach display | Position model |
| --- | --- | --- |
| SL | S1, S2... | Eight-position sleeper pattern |
| 3A | B1, B2... | Eight-position sleeper pattern |
| 2A | A1, A2... | Six-position sleeper pattern |
| 1A | H1, H2... | Numbered berth; type not specified |
| 3E | M1, M2... | Numbered berth; type not specified |
| EC | E1, E2... | Numbered seat |
| 2S | D1, D2... | Numbered seat |
| UR | GS1, GS2... | Existing application inventory only |

SL/3A positions 1-8 repeat Lower, Middle, Upper, Lower, Middle, Upper, Side lower, Side upper. 2A positions 1-6 repeat Lower, Upper, Lower, Upper, Side lower, Side upper. Numbering restarts in each coach. Bay identifies each repeating group. Example: `SL-C1-7` displays **S1 / Berth 7 / Side lower berth**, while `CLASS_3A-C2-3` displays **B2 / Berth 3 / Upper berth**.

These are Railwize's fixed display layouts, not imported rake diagrams. Admin-defined coach capacity is unchanged, including small demonstration coaches. First AC cabin/coupe mixes and 3E variants need explicit coach layouts before reliable berth-type assignment. Window/aisle positions and physical platform coach order are not inferred. UR remains the project's numbered inventory model; real unreserved travel does not guarantee a numbered seat. Stored berth preferences still do not change first-available allocation; no preference guarantee or manual seat selection was added.

Compatibility: original inventory IDs remain stored and used for overlap/locking checks. Existing canonical bookings gain readable labels on retrieval without a migration or reseating. Unknown legacy IDs are preserved as text rather than guessed. Paid WL bookings have no seat until promotion, at which point the response and new email contain their actual allocation. Historical notifications and already queued old email payloads are not rewritten; old email payloads retain their seat-number fallback.

Implementation: reservation `SeatLayouts`, `SeatDetails`, `BookingEngine`, `TicketPdfService`; notification `BookingEmailTemplate`; Angular `shared/seat-assignment.ts` and `pages/ticket.ts`. References: [Indian Railways CAMTECH draft LHB rules, coach class abbreviations, Annexure I](https://indianrailways.gov.in/railwayboard/uploads/directorate/eff_res/camtech/mechanical/YearWise/Draft%20IRCA%20Conference%20Rule%20Pt%20IV%20volume%20II%20(LHB).pdf) and [IRCTC guide with reservation-slip examples](https://contents.irctc.co.in/en/GSA.pdf). Prefixes are class conventions; special/composite rake variants are not modelled.

Restart **reservation-service**, **notification-service** and Angular. No database/table or environment changes are required. Check a confirmed SL/3A/2A ticket, its PDF and a new confirmation email; check WL has no invented seat, then cancel an occupying booking and verify promoted coach/berth descriptions.

Verification: reservation Maven package passed 105 tests; notification Maven package passed 15 tests. Tests cover every supported prefix, repeating layouts and coach boundaries, unknown IDs, unchanged inventory IDs, WL promotion and email fallback. PDF tests render confirmed, waitlisted and long-name tickets and check page bounds; the new six-passenger berth-layout preview fits one page. Angular strict compilation, linked verification bundle and 25 existing frontend unit checks passed. Browser preview uses synthetic API data; live SMTP/payment operations were not repeated. Preview PDF: `reservation-service/target/pdf-preview/railwize-berth-layout.pdf`.

Desktop and 390px mobile browser checks with six synthetic passengers verified coach badges, full berth labels, bay changes, absence of internal IDs and no page-wide horizontal overflow. The regular production build was attempted again and remains blocked by Windows `spawn EPERM`; run `npm run build` locally before deployment.

## Admin workspace, users, bookings and reports (1 October 2026)

Open `http://localhost:4200/admin` after signing in as ADMIN. The new tools are part of the main Angular application and original backend. Administrator access still comes through the existing bootstrap/invitation flow; public registration cannot choose a role.

### Implemented features

- **Overview:** registered accounts (all roles/statuses), catalogue trains, all booking records, cancellations, booking-status breakdown and fare/refund totals. Services load independently; an unavailable service shows an error rather than a fabricated zero.
- **Popular trains:** top ten train numbers by verified paid PNR count, including WL and later cancellations. Unpaid holds do not improve ranking. Counts are bookings, not passengers.
- **User management:** search display/original usernames and email; filter role/status; paginate/sort; view profile details and email; open booking history. Stable account keys preserve history after a Google/display username changes. No passwords, provider IDs or tokens are exposed.
- **Block/reactivate passengers:** explicit review before submission. Backend account locking and session revocation enforce the change. Blocking prevents login/refresh without cancelling existing bookings or stopping refunds. Reactivation requires fresh login and does not bypass verification. Self/admin accounts and INACTIVE/DELETED accounts cannot be modified here. Administrator roles remain invitation-controlled. Changes are logged in auth-service.
- **Booking management:** PNR/account/email search, exact account/train filters, inclusive journey dates, status, pagination and permitted sorting. View / manage opens the existing ticket, PDF, payment/refund state and cancellation review. Normal cancellation fees, refunds, email outbox and WL promotion remain in effect. Admin view provides a return link and does not offer passenger checkout.
- **Revenue/cancellation reports:** inclusive journey-date range of at most 366 days, optional train, grouping by day/month/train, charts and detailed table. Cancellation rate is cancelled PNRs divided by all PNRs in the group, including unpaid/expired records. Empty periods are omitted. Default range covers the current month through approximately 60 days ahead.
- **CSV exports:** users, bookings, cancellations and combined revenue/cancellation reports; UTF-8 BOM for Excel, quoted cells and spreadsheet-formula neutralization. Exports use the last applied filters. Cancellation export fixes status to CANCELLED and preserves other filters. More than 10,000 matching rows is rejected with a request to narrow filters, never silently truncated. CSV opens in Excel; native XLSX is not generated.

Money definitions: captured booking fares require a verified payment association and reliable fare data and include paid WL. Processed refunds and outstanding refunds (including failed refunds awaiting retry) are separate. Net after all refunds = captured booking fares - processed refunds - outstanding refunds. This is a **test-mode booking report**, not a provider settlement ledger or earned-revenue calculation. Duplicate/late captures without the booking's verified payment association and unreliable legacy fares are excluded. Reports group by **journey date**, not payment/refund/cancellation transaction timestamps. Metrics reflect persisted lifecycle/refund state when refreshed.

### APIs and implementation

ADMIN-only Swagger tags: **Admin users** (Auth Service) and **Admin booking reports** (Reservation Service).

| Method | Gateway path | Purpose |
| --- | --- | --- |
| GET | `/api/auth/admin/users` | Filtered/paginated safe account views |
| GET | `/api/auth/admin/users/stats` | Account counts |
| PUT | `/api/auth/admin/users/{accountUsername}/status` | Body: `{"status":"BLOCKED"}` or `{"status":"ACTIVE"}` |
| GET | `/api/auth/admin/users/export` | Filtered users CSV |
| GET | `/api/reservations/admin/dashboard` | All-time booking metrics and popular trains |
| GET | `/api/reservations/admin/bookings` | Filtered/paginated bookings |
| GET | `/api/reservations/admin/bookings/export` | Booking CSV; `cancellations=true` for cancellation report |
| GET | `/api/reservations/admin/reports` | `fromDate`, `toDate`, optional `trainNumber`, `groupBy=day/month/train` |
| GET | `/api/reservations/admin/reports/export` | Same report filters, CSV response |

The service security chains enforce ADMIN independently of Angular guards: anonymous requests receive 401 and passenger requests 403. Cancellation continues through the existing reservation APIs. Implementations: auth `AdminUserController`, `AdminUserService`, `AuthUserRepository`; reservation `AdminBookingController`, `AdminBookingService`, `SecurityConfig`; Angular `pages/admin-insights.ts`, `pages/admin-management.ts`, `core/admin-models.ts`, `core/api.ts`, routes and the existing admin shell/ticket. Aggregation happens in SQL; paginated booking lists do not load passenger collections.

### Restart and verify

Restart **auth-service**, **reservation-service** and Angular; keep the usual gateway, config/discovery, train, user, payment and notification dependencies running. **No database/table migrations, new environment keys or new dependencies are required.**

1. Sign in as ADMIN and check Overview, Users, Bookings and Revenue & reports alongside existing catalogue/invitation tools.
2. Compare dashboard counts to booking lists. An unpaid hold must not add captured fares or paid-train ranking; verified payment should.
3. Find a user with a changed display username; Details must include email and Bookings must retain their original history.
4. Block a disposable test passenger and verify its existing access/refresh tokens fail. Reactivate and sign in afresh. Admin accounts must reject direct status-change requests.
5. On a future test booking, review/confirm cancellation and check the normal refund/email/promotion flow. Refresh reports after state changes.
6. Check date/train/status filters, empty results, sorting and pagination; reversed dates and reports exceeding 366 days must fail clearly.
7. Open each CSV in Excel and check filters, amounts and non-ASCII names. Editing filters without Apply must not change the export scope. Passenger tokens must fail on all admin data/export APIs.

Validation: full Maven packages completed with **112 reservation tests and 52 auth tests, no failures/errors/skips**. New H2/HTTP checks cover totals, unpaid/legacy exclusion, refund accounting, date/train/status filters, sorting, empty reports, CSV escaping, role restrictions, protected accounts and session revocation/reactivation. Angular strict AOT/templates, linked verification bundle and 25 existing unit checks passed. Synthetic desktop browser checks covered dashboard, profile details, block confirmation/status refresh, stable-account history and reports; the 390px report layout has no page-wide overflow. Frontend `tests/browser/admin.spec.ts` contains repeatable Playwright scenarios. The embedded browser did not return a CSV file-save event; backend CSV content/security were verified, but final browser-to-disk export needs local verification. Standard Angular production build remains blocked here by Windows `spawn EPERM`; run `npm run build` and `npm run test:e2e` locally before deployment. No live account was blocked, live booking cancelled, or SMTP/Razorpay operation repeated for this admin work.

## Tests, logging and dependency resilience (1 October 2026)

- **JUnit and Mockito already cover the backend.** Service unit tests mock repositories/clients; HTTP and lifecycle tests use isolated H2 storage and controlled external dependencies. `Verify-Backend.ps1` now also includes chatbot-service, which was missing from the all-service runner. Run `.\Verify-Backend.ps1` from this backend folder, or `mvn test` inside one service. The optional live Gemini smoke test stays opt-in; ordinary tests use no real payments, emails or provider quota.
- **SLF4J/Logback are already in use.** Application service logs go to `logs/<service>.log`, relative to the service working directory; Config Server and Eureka have console Logback configurations. API errors include request references; background delivery/payment failures and admin status changes are logged. Central configuration now caps rolling service logs at 10 MB per archive, 14 days and 200 MB total archived history per service. This archive cap does not include the active file. PNR generation no longer prints the ticket reference. Never enable unrestricted request/response-body logging or paste secrets from diagnostic output.
- **PNR allocation now actually uses Resilience4j.** `reservation-service/.../service/PnrGateway.java` wraps the Feign call with the registry's `pnrService` breaker. Previously there were settings but the booking path bypassed the breaker. Central settings retain a 10-call window, 5 minimum calls, 50% failure threshold, a 15-second open interval and 3 half-open probes. PNR Feign connection/read timeouts are 2/5 seconds. Downstream failures, an open circuit or an invalid PNR return HTTP 503 instead of creating a booking. No automatic retry, generated fallback PNR or fake booking success is introduced. The existing Gemini circuit breaker remains in place. Other remote calls are not automatically covered by this PNR breaker.
- **Regression tests:** `PnrGatewayTest` uses JUnit/Mockito and real Resilience4j to check successful allocation, opening after failures, skipping downstream calls while open, half-open recovery and invalid responses. Existing booking lifecycle/HTTP tests exercise the connected booking path.
- **Frontend:** the separate Angular application's `npm test` now runs Jasmine with 34 unit specs; see its README for commands, scope and browser-test instructions.
- **SonarQube is deferred**, as requested. No server, scanner or quality-gate claim is included.

Restart Config Server, then reservation-service and pnr-service to load the PNR changes. Other application services pick up central log-retention settings on restart. No database, API payload, credentials or frontend visual changes are needed.
