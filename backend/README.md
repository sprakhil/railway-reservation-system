# Railwize Backend

I designed the Railwize backend as a set of Spring Boot services with separate responsibilities for identity, catalogue, timetables, reservations, payments and notifications. My goal is to make the booking lifecycle explicit: availability is checked on the server, payments are verified independently of the browser, and waitlist/refund updates continue in background jobs.

This backend serves the [Angular application](../frontend/README.md). For the product overview and demonstration flow, see the [main README](../README.md).

**All payments and refunds use Razorpay test mode.** The application maintains its own catalogue/inventory and does not reserve seats in Indian Railways' live system.

## Services and databases

The project has 12 independently runnable Maven services. There is no root Maven reactor; run Maven inside a service directory or use the provided verification helper.

| Service | Default port | MySQL schema | Responsibility |
| --- | --- | --- | --- |
| `config-server` | 8888 | — | Central configuration from bundled property files; secured access |
| `eureka-server` | 8761 | — | Service registration and discovery; secured dashboard |
| `api-gateway` | 8080 | — | Browser-facing routes, CORS, request references, aggregated Swagger |
| `auth-service` | 8081 | `railway_auth_db` | Registration/OTP, credentials/Google login, sessions, username choice, admin invitations and user management |
| `user-service` | 8082 | `railway_user_db` | Personal profiles and owner-scoped saved passengers |
| `train-service` | 8083 | `railway_train_db` | Stations/coordinates, trains, operating days, class inventory and fare parameters |
| `schedule-service` | 8084 | `railway_schedule_db` | Validated, ordered train timetables with cumulative distance and overnight days |
| `reservation-service` | 8085 | `railway_reservation_db` | Search/quotes, journey alternatives, booking lifecycle, inventory, tickets, notifications, run cancellation and booking reports |
| `pnr-service` | 8086 | `railway_pnr_db` | Unique PNR generation for internal reservation use |
| `payment-service` | 8087 | `railway_payment_db` | Razorpay test orders, verification, webhook processing, reconciliation and refunds |
| `notification-service` | 8088 | `railway_notification_db` | Durable booking-email receipt, delivery records, templates and SMTP retries |
| `chatbot-service` | 8089 | — | Read-only Gemini assistant, RAG, live tools, streaming and process-local conversation state |

Service-owned schemas separate persistence responsibilities. Normal application communication uses APIs/messages rather than direct cross-service database joins. The optional catalogue import deliberately inserts train and schedule data together as an offline setup operation.

## Core technologies

Java 21 source level, Spring Boot 4.1.1, Spring Cloud 2025.1.3, Spring Security, Spring Data JPA/Hibernate, Bean Validation, MySQL 8, RabbitMQ, OpenFeign, WebClient/HTTP clients, Resilience4j and SLF4J/Logback. PDFs use Apache PDFBox. The chatbot uses Spring AI 2.0.1 and the Gemini Developer API with WebFlux/SSE.

Exact dependency versions are maintained in each service's `pom.xml`. Docker, WSL and Redis are not required for the local configuration.

## Accounts, security and administrator access

### Registration, login and recovery

- Registration creates a passenger account and sends a verification OTP. Verification activates the account and triggers a welcome email; the frontend then directs the user to login.
- Local login accepts the supported username/email identifier and password. Passwords are BCrypt-hashed.
- Registration and password-reset OTPs have separate purposes, are hashed, expire after five minutes and allow at most five incorrect attempts. Resending has a 60-second cooldown per account/purpose.
- Password recovery verifies the reset code, updates the password and revokes sessions. A Google-only account is not silently given a password through the reset flow.
- Multiple logins can coexist. `logout` revokes the current session; `logout-all` revokes all of the account's sessions.
- JWT access tokens contain a session reference. Protected resource requests check session/account state with auth-service, so revoked, blocked or stale-role sessions are rejected. If that dependency is unavailable, protected requests fail closed.

### Google sign-in

The supported flow begins at `GET /api/auth/google/start`. Auth-service maintains browser-bound state/nonce, checks the Google identity and redirects to the Angular callback with a short-lived, single-use exchange code. Angular exchanges that code in the same browser session. Access/refresh tokens are never put in the redirect URL.

Configure these two different addresses correctly:

| Setting | Local value |
| --- | --- |
| Google client's authorized redirect URI / `GOOGLE_REDIRECT_URI` | `http://localhost:8080/api/auth/google/callback` |
| `FRONTEND_CALLBACK_URI` | `http://localhost:4200/auth/callback` |

Use `localhost` consistently. State is currently local to the auth-service instance; scaling this flow requires appropriate session affinity/shared storage. The obsolete `POST /api/auth/google` returns 410. Account-linking rules are described in [AUTH-INTEGRATION.md](auth-service/AUTH-INTEGRATION.md).

### Profile and username

User-service exposes the signed-in profile at `/api/users/me`, including the verified email obtained from authenticated account context. If a valid account has not saved personal details, lookup returns an empty profile view rather than 404; `PUT /me` creates or updates the stored details. Profile ownership remains tied to the immutable account key.

An active verified account can make **one** display/login username change through `/api/auth/username`. Names are normalized to lowercase and must contain 4–20 letters, digits or underscores. Conflicts are rejected, and an identical retry is safe. I chose a single change to personalize generated Google usernames while avoiding repeated identity changes; existing booking ownership is preserved.

### Administrators

Public registration and new Google accounts do not choose a role. For the first administrator, enable `BOOTSTRAP_ADMIN_ENABLED` with explicit bootstrap credentials, start auth-service once, then disable bootstrap and restart it. There is no built-in shared administrator password.

Additional administrators join by invitation:

1. An ADMIN invites an email address.
2. The service stores a hash of a random invitation secret and emails the invitation link.
3. The recipient registers/verifies that address or signs into the matching verified account.
4. The recipient accepts the single-use invitation within its 24-hour validity.
5. Existing sessions are revoked after elevation; a fresh login grants the administrator session.

Invitations can be listed and unused invitations revoked. The administrator portal is part of Angular at `/admin`; backend authorization applies regardless of which client sends a request.

## Stations, trains and schedules

### Catalogue

Station codes are trimmed and normalized to uppercase. Stations include name, city, state, railway zone and optional coordinates. Latitude and longitude must be supplied together and be within valid ranges; they power nearby-station suggestions.

Each bookable train needs:

- A unique train number, name and category.
- Different valid source/destination station IDs.
- Source-departure operating weekdays.
- Supported seat classes and a configuration for every declared class.
- Coach count, seats per coach, base fare, per-kilometre rate and minimum fare.
- A complete published timetable matching the train's endpoints.

Supported class values are `SL`, `CLASS_3A`, `CLASS_2A`, `CLASS_1A`, `CLASS_3E`, `EC`, `CLASS_2S` and `UR`. CC is not a supported enum. Capacity is configured as 1–30 coaches and 1–100 positions per coach.

Once configured/published, train number, route endpoints, operating days and seat capacity are protected from changes that would invalidate existing inventory. Editable catalogue fields and fare parameters remain subject to service validation. Station codes are stable identifiers for routes.

### Timetables and distance

A route has 2–200 ordered, unique station stops. Stop sequences start at 1 and are consecutive. The source starts at distance 0 on Day 1 with **no arrival time**; the destination has **no departure time**. Intermediate arrivals must follow the previous departure, and cumulative distances must strictly increase, up to 20,000 km.

Arrival and departure have separate day numbers, allowing a stop to arrive at 23:55 on Day 1 and depart at 00:05 on Day 2. Days are limited to 1–30 and times use whole seconds. The service checks that stations exist and the first/last station matches the train catalogue. A published timetable is immutable; submitting an identical route is safe, but replacing it is rejected.

Distance belongs to the **train's route stops**, not a single station. A station can be a different number of kilometres from the source on different routes.

## Search, fares and journey alternatives

Direct search accepts station codes, passenger count, seat class and the passenger's **boarding date**. Both stations must appear in the correct order on the published route, the class must be configured, and the train must operate on the calculated source run date.

The allowed boarding window is today through **today plus two calendar months, inclusive**, in `Asia/Kolkata`; this is not a fixed 60 days. Boarding departure must still be in the future. Source run date is derived using the boarding stop's departure-day offset, so a Day-2 passenger may board on Tuesday on a train that originally departed Monday.

Fares are calculated with exact decimal values:

```text
segmentDistance = destination.distanceKm - boarding.distanceKm
farePerPassenger = max(minimumFare, baseFare + ratePerKm * segmentDistance)
totalFare = farePerPassenger * passengerCount
```

The result is rounded to two decimal places and saved in the booking. Later catalogue-price changes do not rewrite the booking's price snapshot. `expectedTotalFare` in the booking request protects the passenger against silently accepting a changed quote.

### Connecting and nearby journeys

`GET /api/reservations/alternatives` searches one interchange at the **same station code**, optionally widening the origin and destination using coordinates and Haversine distance. It validates both legs' class, date, timetable, availability and transfer window.

| Parameter/rule | Backend limit/default |
| --- | --- |
| Nearby radius | 1–100 km; default 50 |
| Minimum transfer | 30–240 minutes; default 60 |
| Maximum transfer | At least the minimum and at most 720 minutes; default 360 |
| Alternative stations | Up to four nearest at each endpoint, plus the exact station |
| Catalogue bound | Up to 200 trains; station paging also has a bounded scan |
| Returned alternatives | Up to 20, with partial-result notices if limits are reached |

Plans show the actual stations, dates/times, transfer wait, each fare and total fare, and CNF/WL availability. Cancelled runs are excluded. The planner does not invent road travel times, allow a cross-city interchange, or reserve both legs. Each leg is booked and paid separately through the standard flow.

## Reservation lifecycle and concurrency

I use whole-booking allocation: each passenger gets a seat when confirmed, but all passengers in a booking share one allocation state. There is no RAC, mixed CNF/WL group or individual-passenger cancellation.

| Booking state | Meaning |
| --- | --- |
| `PENDING_PAYMENT` | A booking exists with a limited checkout deadline |
| `CONFIRMED` | Verified paid booking with all required seats; shown as CNF |
| `WAITING` | Verified paid booking waiting for complete allocation; shown as WL |
| `EXPIRED` | Unpaid checkout deadline passed |
| `CANCELLED` | Passenger/operator cancellation or a paid WL booking closed at cutoff |

Payment and refund states are separate from these booking states.

### Hold and payment acceptance

1. The server validates the route, price and **1–6 non-null passengers**.
2. `POST /api/reservations/book` requires an `Idempotency-Key`. Retries reuse the same key/body; a new booking needs a new key.
3. Availability is rechecked under database locks. The default hold is **two minutes**, capped by applicable departure/WL deadlines.
4. A CNF checkout holds seats; a WL checkout holds neither seats nor a paid queue position.
5. Only verified captured test payment activates confirmation or paid waitlist enrollment.
6. An expired booking is not revived by a late capture; the captured amount follows the full-refund path.

Seat assignment checks every segment in the requested journey. Non-overlapping journeys may reuse a berth, while overlapping journeys cannot. A run-wide lock is acquired before class inventory locks so booking, expiry, payment acceptance, promotion and operator cancellation coordinate their updates. Database transactions protect local state; calls across services are reconciled rather than treated as one distributed transaction.

### Waitlist and promotion

Paid WL priority follows increasing booking creation ID within a train source-run date and class, across its route segments. Position counts bookings rather than passengers. The oldest paid WL booking must fit completely; smaller/later bookings do not bypass it, even if that could improve seat utilization.

Promotion requires no second payment or passenger action and sends confirmation with allocated seats. Lifecycle processing runs during relevant API operations and in the scheduled expiry scan, **every ten seconds by default**.

WL closes **one hour before each booking's boarding time** by default. Remaining paid WL bookings are cancelled and fully refunded; new WL checkout is rejected after cutoff. A release exactly at cutoff does not override expiry. Both hold and cutoff durations are configurable.

### Cancellation and refunds

`GET /api/reservations/{pnr}/cancellation-preview` returns the current estimate. `DELETE /api/reservations/{pnr}` cancels the whole owned booking, or an administrator-authorized booking, before boarding.

- Paid CNF passenger cancellation uses the configured deduction, **20% by default**.
- Paid WL cancellation has no deduction; unpaid cancellation has nothing to refund.
- The status at actual cancellation determines the fee. A WL booking promoted after preview may have a different estimate.
- Repeated cancellation must not generate a duplicate refund.
- Refunds are asynchronous. `PENDING` is not equivalent to completed credit, including in test mode.

### Dated train cancellation

An ADMIN can preview and cancel one future train run using **train number + original source departure date in IST**. Cancellation is allowed within the two-calendar-month window and only before departure from the first station. A 5–500-character reason is required.

One reservation-database transaction records the operator/reason/time, cancels active CNF/WL/unpaid bookings across all classes/segments and writes notification events. Paid bookings receive a full test refund with **zero cancellation fee**. Late captures on a cancelled run are refunded in full. Already cancelled/expired bookings are not reprocessed.

Quotes/search/planning exclude the cancelled run, and final booking allocation rechecks under the run lock. Promotion cannot confirm tickets on a cancelled run. Repeat cancellation preserves the original audit decision. Ambiguous legacy active bookings require review instead of guessing their run.

This action is permanent for that date. It does not delete the train, cancel every future run, reinstate a service, cancel only part of the route or cancel an independently booked onward train. Other dates remain available.

## Payments and provider reconciliation

Payment-service creates orders from the server's saved booking amount, validates checkout signatures and verifies provider state. Webhook processing, duplicate handling and scheduled reconciliation cover missing browser callbacks, repeated notifications, late/extra captures and asynchronous refunds.

The provider keys must begin with `rzp_test_`; there is no live-mode switch. Missing/invalid configuration makes checkout unavailable rather than generating a fake successful payment. A payment response marked successful can correspond to `WAITING`, so clients must inspect booking status separately.

For webhooks, configure a reachable test endpoint ending in `/api/payments/webhook` and a matching test webhook secret. A provider cannot call a laptop's private localhost directly. Reconciliation also queries test-provider state and does not depend exclusively on browser callbacks.

## Saved passengers, tickets and notifications

### Master List and history

User-service supports owner-scoped saved-passenger create/read/update/delete. Records include name, age, gender and optional berth preference. Age is stored rather than date of birth, so it should be checked when reused.

Both booking and saved-passenger validation accept `LOWER`, `MIDDLE`, `UPPER`, `SIDE_LOWER`, `SIDE_UPPER`, `NONE`, or an omitted/null preference. The saved field is `berthPreference`; the booking DTO's legacy field is `birthPreference`. Preferences are informational and do not guarantee a particular berth. Booking passengers are independent snapshots.

History and catalogue APIs support pagination/sorting; booking history adds status and inclusive **journey-date** filters. Standard page responses contain `content`, `page`, `size`, `totalElements`, and `totalPages`, with zero-based pages and maximum size 100. Timetables remain ordered stop lists. Unsupported sort fields, invalid sizes or reversed date ranges are rejected.

### PDF and seat descriptions

Authenticated ticket download at `/api/reservations/{pnr}/ticket.pdf` requires owner/admin access and verified payment. The PDF contains the Railwize branding, PNR/status, route/boarding time, passenger table, coach/berth descriptions, fare/refund information, timestamp and page numbers. It reflects the current state at download time and handles multi-page content.

SL/3A use an eight-position lower/middle/upper/side pattern; 2A uses a six-position lower/upper/side pattern. Coach labels use S, B, A, H, M, E, D and GS for their supported classes. 1A/3E do not guess detailed berth types where a layout is not defined. The display layer does not change capacity or stored allocations.

PDFBox embeds bundled Lato fonts from [ticket assets](reservation-service/src/main/resources/ticket). Font license/provenance files are included. Leave `PDF_FONT_PATH` blank for portable offline generation. Complex-script shaping remains limited; unsupported glyphs have a visible fallback. Calendar `.ics` generation is a frontend feature using the backend departure timestamp/status.

### Email delivery

Account emails include registration OTP, welcome, password-reset OTP/instructions and admin invitations, with consistent Railwize branding. Auth-service handles account mail; notification-service handles booking lifecycle deliveries.

| Booking event | Email information |
| --- | --- |
| Verified payment / booking confirmation | PNR, train/route, date/time, CNF or WL state, fare and passenger details; PDF ticket attachment |
| WL promotion | Updated confirmation, allocated seats and an updated PDF attachment |
| Reported payment failure / incomplete checkout | Failure or expiry guidance and booking link; retry only while a hold remains valid |
| Passenger cancellation | Affected booking/passengers, deduction and refund amount/state |
| WL cutoff / operator train cancellation | Cancellation reason and full-refund information where payment was captured |
| Refund initiated, processed or delayed | Current test-refund progress; completion is reported after provider confirmation |

Reservation-service stores email events durably with the booking change. A worker publishes through RabbitMQ with confirmation, falling back to an authenticated HTTP handoff when necessary. Notification-service records event IDs durably, suppresses duplicate successful deliveries and retries SMTP failures up to five attempts. A narrow crash-after-send window can still duplicate mail; SMTP is not an exactly-once transaction. Email failure does not reverse a booking.

### In-app notifications

Reservation-service stores a separate durable, account-owned inbox for booking/payment/promotion/cancellation/refund events. APIs support paging, unread filtering and individual/all read actions. Angular polls every five seconds while active and refreshes after related actions. In-app updates do not wait for successful SMTP delivery. Existing historical mail is not automatically backfilled as inbox events.

## Administrator management and reports

- **Dashboard:** accounts, trains, booking/cancellation totals, status breakdown and fare/refund summaries. Service errors remain visible rather than becoming invented zero counts.
- **Popular trains:** top ten by verified paid PNR count, including paid WL and later cancellations; unpaid holds do not improve ranking.
- **User management:** search username/email, role/status filters, sorting/paging, personal details and stable-account booking history.
- **Block/reactivate:** eligible passenger accounts can be blocked/reactivated after review. Blocking revokes sessions but does not cancel existing tickets or stop refunds. Self/admin accounts and inactive/deleted accounts are protected from this management action; reactivation requires fresh login and does not bypass verification.
- **Booking management:** PNR/account/email search, exact train/account filters, status and journey dates, ticket/PDF review and normal cancellation.
- **Reports:** inclusive journey-date ranges up to 366 days, optional train filter and day/month/train grouping. Cancellation rate is cancelled PNRs divided by all PNRs in the group, including unpaid/expired records. Empty periods are omitted.
- **Exports:** users, bookings, cancellations and combined financial/cancellation reports as UTF-8 CSV with BOM, quoted values and spreadsheet-formula neutralization. More than 10,000 matching rows is rejected rather than silently truncated. CSV opens in Excel; native XLSX is not generated.

Financial definitions:

```text
captured booking fares = verified paid booking fares, including paid WL
net after all refunds = captured booking fares - processed refunds - outstanding refunds
```

Outstanding includes refunds still pending or failed awaiting action. These are **test booking reports grouped by journey date**, not earned-revenue or Razorpay settlement reports. Unreliable legacy fares and extra/late captures without the booking's verified payment association are excluded.

## Railwize AI assistant

I kept the assistant portable: it uses the **Gemini Developer API**, an in-memory vector store, in-memory conversation storage and a lightweight semantic cache, with no Redis or Docker dependency.

| Capability | Implementation |
| --- | --- |
| RAG | `KnowledgeBase` loads [railwize-faq.md](chatbot-service/src/main/resources/knowledge/railwize-faq.md), embeds sections and indexes Spring AI `SimpleVectorStore` |
| Live tools | `RailwayTools` calls existing services to resolve stations, search trains and read the signed-in account's booking information |
| Account questions | Booking lists, cancellations, counts and next-journey queries can take a direct service-backed path without an LLM call |
| Conversation memory | `MessageWindowChatMemory` with `InMemoryChatMemoryRepository`, 20 messages and a 24-hour conversation lifetime |
| Streaming | Authenticated WebFlux SSE with `status`, `delta`, `cards`, `sources`, `fallback` and `done` events |
| Structured output | `BeanOutputConverter` describes text-paragraph JSON; paragraphs are validated before release; trusted service data builds UI cards |
| Semantic cache | Bounded to 128 entries/30 minutes, similarity threshold 0.96 and qualifier checks for eligible first-turn public policy questions |
| Guardrails | Input screening, sensitive-data masking/aliasing, restricted tools and output validation |
| Resilience | Gemini circuit breaker, timeouts and explicit fallback/error categories |

Private booking answers and live availability are not served from the shared policy cache. Conversations are checked against the authenticated account. The assistant is **read-only**: it cannot book, cancel, refund or modify accounts. It does not treat model text as authoritative live inventory or generate trusted card links from arbitrary model output.

Vector indexing retries when embeddings are unavailable, and FAQ keyword fallback can still supply context. A failed embedding request is not proof that chat quota is exhausted. Provider 429 responses are described as rate/quota limits rather than automatically claiming the daily allowance is gone.

Configure `GEMINI_API_KEY`, `GEMINI_CHAT_MODEL` and `GEMINI_EMBEDDING_MODEL` in the backend environment. Their repository defaults are in `.env.example`; model access and free-tier availability depend on the Google account. Check `/api/chat/status` for configuration, RAG readiness, circuit state and last failure category. Memory, cache and index reset on chatbot-service restart.

## API reference and Swagger

Open **http://localhost:8080/swagger-ui/index.html** and select a service definition. Public, owner and ADMIN operations have descriptions/examples; internal service-token operations are hidden from ordinary public documentation. Config Server and Eureka are infrastructure components rather than passenger API groups.

Register/verify/login first, copy `accessToken`, select **Authorize**, and paste the token without the `Bearer ` prefix. The shared `bearerAuth` scheme applies across definitions in that gateway explorer. A padlock or an Authorize dialog alone is not proof that the backend accepted a request.

- Swagger remembers the token on refresh within the same gateway run.
- Successful backend logout from the explorer clears its persisted token.
- A gateway restart clears saved Swagger authorization and requires a fresh token entry. Restarting only a downstream service does not do this; it is a Swagger browser-state rule, not revocation of every backend session.
- The Authorize dialog's Logout button clears local Swagger state; call the logout API to revoke the server session.
- Login/refresh responses do not automatically replace Swagger's saved token. Direct-service Swagger pages on different ports have separate origins.

### Main gateway endpoints

| Area | Routes |
| --- | --- |
| Authentication | `POST /api/auth/register`, `/verify-otp`, `/login`, `/refresh`, `/reset-password`; resend/forgot operations use the `email` query parameter |
| Sessions/username | `POST /api/auth/logout`, `/logout-all`; `GET/PUT /api/auth/username` |
| Google | `GET /api/auth/google/start`, `/callback`; `POST /api/auth/google/exchange` |
| Profiles | `GET/PUT /api/users/me`; owner/admin profile lookup under `/api/users/{username}` |
| Master List | `GET/POST /api/users/me/passengers`; `PUT/DELETE /api/users/me/passengers/{id}` |
| Catalogue | `GET/POST /api/stations`, `/api/trains`; station ID/code and train-number lookups; ADMIN updates |
| Timetable | `GET/POST /api/schedules/{trainNumber}`; publication requires ADMIN |
| Search | `GET /api/reservations/search`, `/quote`, `/alternatives` |
| Booking | `POST /api/reservations/book`; `GET /api/reservations/my-bookings`, `/my-bookings/overview`, `/pnr/{pnr}` |
| Ticket/cancellation | `GET /api/reservations/{pnr}/ticket.pdf`, `/{pnr}/cancellation-preview`; `DELETE /api/reservations/{pnr}` |
| Payment | `POST /api/payments/create-order`, `/verify`, `/webhook`; `GET /api/payments/{pnr}/status` |
| Notifications | `GET /api/reservations/notifications`; `PUT /{id}/read` and `/read-all` under that path |
| Admin users | `GET /api/auth/admin/users`, `/users/stats`, `/users/export`; `PUT /users/{accountUsername}/status` |
| Admin bookings/reports | `GET /api/reservations/admin/dashboard`, `/bookings`, `/bookings/export`, `/reports`, `/reports/export` |
| Admin run cancellation | `GET /api/reservations/admin/train-runs`, `/{trainNumber}/{runDate}`; `POST /{trainNumber}/{runDate}/cancel` |
| Admin invitations | `GET/POST /api/auth/admin/invitations`, `DELETE /{id}`; `POST /api/auth/admin-invitations/accept` |
| Assistant | `/api/chat/status`; create/read/delete conversations; `POST /api/chat/conversations/{id}/messages` streams a reply |

Payment webhooks use provider signature validation rather than a passenger bearer token. PNR generation and internal service calls are not public account operations. For exact DTOs and access rules, use the generated Swagger definition and current controller source.

### Example booking flow

After importing the India dataset, request a fresh quote for `RW907`, `JAT → SVDK`, `SL`, one passenger on a future date. This sample fare is INR 100; always use the value returned by the actual quote.

```http
GET /api/reservations/quote?trainNumber=RW907&fromStation=JAT&toStation=SVDK&dateOfJourney=YYYY-MM-DD&seatClass=SL&passengers=1
```

Submit the following body to `/api/reservations/book` with your bearer token and a new `Idempotency-Key` header. Replace the date before execution:

```json
{
  "trainNumber": "RW907",
  "fromStation": "JAT",
  "toStation": "SVDK",
  "dateOfJourney": "YYYY-MM-DD",
  "seatClass": "SL",
  "expectedTotalFare": 100.00,
  "passengers": [
    {"name": "Aarav Sharma", "age": 28, "gender": "MALE", "birthPreference": "LOWER"}
  ]
}
```

Create the payment order with `{"pnr":"<returned-pnr>"}`, complete the actual Razorpay **test** checkout, and send the real signed checkout response to `/api/payments/verify`. Do not manufacture order/payment IDs or signatures. Read booking and payment status to distinguish CNF, WL, expiry and refund progress.

More request templates are in [demo-api.http](demo-api.http). That file uses the separate `Seed-DemoCatalogue.ps1` dataset; the [India guide](../Railwize-india-data/INDIA-DATA-GUIDE.txt) has matching examples for the larger catalogue.

## Error handling, validation and observability

Service exception handlers, authentication failures and gateway errors use a consistent response shape:

```json
{
  "code": "BAD_REQUEST",
  "status": 400,
  "message": "Request validation failed",
  "fieldErrors": {"passengers[0].age": "must be greater than or equal to 1"},
  "timestamp": "2026-10-11T10:00:00Z",
  "requestId": "request-reference"
}
```

`X-Request-ID` is returned/forwarded for tracing, and stack traces are not exposed in responses. Validation covers normalized same-station searches, past/too-distant boarding dates, invalid passenger count/null entries, inconsistent preferences, malformed schedules, unsupported classes/sort fields and authorization boundaries.

SLF4J/Logback records application and background-job failures. Application logs use `logs/<service>.log` relative to each service directory. Shared rolling configuration limits archives to 10 MB each, 14 days and 200 MB total archive history per service; the active file is additional. Config Server/Eureka have console logging configurations.

Resilience4j protects PNR allocation and Gemini calls. The PNR breaker uses a 10-call window, at least five calls, a 50% failure threshold, a 15-second open interval and three half-open probes; Feign connect/read timeouts are 2/5 seconds. Failure returns 503 instead of fabricating a PNR or booking. These breakers do not automatically wrap every service dependency.

## Environment configuration

Create **`backend/.env`**, using [.env.example](.env.example) as the key reference. `New-DevEnvironment.ps1` can generate private local configuration/discovery/JWT/internal-service secrets without overwriting an existing file.

| Keys | Purpose |
| --- | --- |
| `CONFIG_SERVER_URL`, `CONFIG_SERVER_USERNAME`, `CONFIG_SERVER_PASSWORD` | Configuration connection/authentication |
| `EUREKA_USERNAME`, `EUREKA_PASSWORD` | Discovery server/client authentication |
| `DB_USERNAME`, `DB_PASSWORD`, `DDL_AUTO` | Database login and schema mode; database names/URLs are in central service properties |
| `JWT_SECRET`, `RAILWAY_INTERNAL_TOKEN` | Token signing and authenticated internal calls; each must meet the 32-byte/character minimum enforced by the launcher |
| `AUTH_SERVICE_URL`, `RESERVATION_SERVICE_URL` | Service addresses used by configured direct HTTP integrations |
| `RABBITMQ_ADDRESSES`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | Broker connection |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP account and transport |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | Google web OAuth client |
| `FRONTEND_CALLBACK_URI`, `FRONTEND_ORIGINS`, `FRONTEND_BASE_URL` | Browser callback, allowed origins and links in emails |
| `SESSION_COOKIE_SECURE` | False for local HTTP; true for an HTTPS deployment |
| `RAZORPAY_TEST_KEY_ID`, `RAZORPAY_TEST_KEY_SECRET`, `RAZORPAY_TEST_WEBHOOK_SECRET` | Test payment credentials/signatures |
| `BOOTSTRAP_ADMIN_ENABLED`, `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Explicit first-admin creation |
| `ADMIN_INVITATION_URI` | Frontend invitation acceptance address |
| `PDF_FONT_PATH` | Optional local font override; blank uses bundled fonts |
| `GEMINI_API_KEY`, `GEMINI_CHAT_MODEL`, `GEMINI_EMBEDDING_MODEL`, `CHATBOT_PORT` | Assistant provider/models and port |

The root `.env` is loaded by **`Run-Service.ps1`**, not automatically by Spring Boot. An IDE Run button or plain Maven command needs the same variables explicitly configured/inherited. All services, including Config Server, must receive the applicable environment values. Values referenced by central configuration do not make an unread local `.env` file magically available to a JVM.

Use plain `KEY=value` lines for the supplied loader. Keep keys/secrets out of source code, frontend assets, screenshots and logs. Blank optional provider settings do not enable those integrations.

## Running the services

### Prepare databases

Install JDK 21 or a compatible newer JDK, Maven 3.9+, MySQL 8 and native RabbitMQ/Erlang. Start MySQL/RabbitMQ, then run [Create-Databases.sql](Create-Databases.sql). It creates the eight schemas listed above without dropping existing data.

For fresh local databases, `DDL_AUTO=update` lets the current services create tables. Existing installations need a backup and a review of applicable manual migrations; do not run legacy archive/upgrade scripts indiscriminately against an already upgraded schema.

### PowerShell helper

From this `backend` folder, start each command in a **separate terminal**. They are long-running processes, not one sequential startup script:

```powershell
.\Run-Service.ps1 config-server
.\Run-Service.ps1 eureka-server
.\Run-Service.ps1 auth-service
.\Run-Service.ps1 user-service
.\Run-Service.ps1 train-service
.\Run-Service.ps1 schedule-service
.\Run-Service.ps1 pnr-service
.\Run-Service.ps1 reservation-service
.\Run-Service.ps1 payment-service
.\Run-Service.ps1 notification-service
.\Run-Service.ps1 chatbot-service
.\Run-Service.ps1 api-gateway
```

Wait for Config Server/Eureka first, then application service registration before using the gateway. Avoid starting duplicate instances from both the IDE and terminal. Config Server/Eureka require their infrastructure credentials; passenger credentials are not used for those dashboards.

### IntelliJ Run button

1. Open/import the backend service Maven projects and let dependencies resolve.
2. Select a compatible project JDK.
3. Create an Application/Spring Boot run configuration for each service's class containing `@SpringBootApplication`.
4. Use that service directory as the working directory and its module classpath.
5. In **Environment variables**, configure the values from your private backend `.env`. If the installed IDE supports an environment-file loader, explicitly select that file; otherwise enter the variables in the run configuration. Merely creating `.env` does not load it.
6. Run Config Server, Eureka, the application services and gateway in the order above.

### Eclipse/STS Run button

Import the service projects through **Existing Maven Projects**, select the JDK, and open **Run Configurations → Java Application** (or Spring Boot App in STS). Select the service's main class, set its working directory and enter the private variables in the **Environment** tab. Repeat for each service, then start in the same order.

### CMD without unsigned scripts

In each CMD terminal, set the required environment variables using `set "KEY=value"`, then run Maven inside that service directory:

```bat
cd C:\path\to\Railwize\backend\auth-service
mvn spring-boot:run
```

Replace the path/service and configure the complete required environment from the table above. `set` affects that terminal and its child processes; other open terminals do not inherit it. Do not paste real credentials into shared documentation or committed launch files. The IDE environment configuration is usually more convenient than repeatedly entering variables.

### Catalogue setup and second-laptop use

Use Admin screens or the [India SQL dataset](../Railwize-india-data/LOAD-INDIA-CATALOGUE.sql) after the schema exists. Its [guide](../Railwize-india-data/INDIA-DATA-GUIDE.txt) explains safe loading, existing-data conflicts and demonstrations. A station/train record alone does not make a service searchable: operating days, configured class fares/capacity and a valid timetable are required.

A repository ZIP contains source, not database contents or credentials. To preserve a running installation on another laptop, stop application services and export/import all eight schemas as a consistent set. Transfer `.env` privately and update machine-specific configuration.

## Schema migrations

Manual migration sources are retained for existing databases:

| Service | Location |
| --- | --- |
| Authentication | [database](auth-service/database) — account/OTP, sessions and admin invitations |
| User profiles | [database](user-service/database) — saved passengers |
| Trains | [manual migrations](train-service/src/main/resources/db/manual-migrations) — inventory/fares and coordinates |
| Schedules | [manual migrations](schedule-service/src/main/resources/db/manual-migrations) — departure days/route constraints |
| Reservations | [manual migrations](reservation-service/src/main/resources/db/manual-migrations) — lifecycle, WL/events, in-app notifications and dated run cancellation |
| Payments | [migration sources](payment-service/db/migration) — legacy preservation and test-payment schema |
| Notifications | [database](notification-service/database) — durable delivery inbox |

These are reviewed/manual SQL files, not a claim that an automatic migration runner applies them. MySQL DDL is not transactional. Back up existing data, determine the actual schema version and follow the corresponding integration notes. Stop old reservation instances before upgrading the train-run locking protocol; do not mix old and new booking engines against the same live inventory.

## Tests

Run all service suites from `backend` using the optional PowerShell helper:

```powershell
.\Verify-Backend.ps1
.\Verify-Backend.ps1 -Integration
```

Without PowerShell, run this inside any service directory:

```text
mvn test
```

The integration switch runs a selected list of integration suites; the complete runner executes each service's normal Maven test phase. Tests use JUnit/Mockito, isolated H2 storage where applicable, and mocked/stubbed downstream/provider boundaries. They cover authentication/OTP/session rules, access control, DTO validation, catalogue/paging, schedules, booking concurrency/lifecycle, payment/refund handling, notifications, admin reports/CSV, train-run cancellation, chatbot ownership/streaming and circuit breakers.

Gateway Swagger browser-state tests can be run from `api-gateway` with:

```text
node --test src/test/js/swagger-session.test.cjs
```

The Gemini live smoke test is opt-in; ordinary suites should not consume provider quota or send real mail. Isolated tests do not replace a live MySQL concurrency check or configured Google/SMTP/RabbitMQ/Razorpay/Gemini smoke test. SonarQube is not configured in this version.

## Where I implemented the main rules

Class names below are under the service's `src/main/java` package; tests are under `src/test/java`.

| Responsibility | Service / main classes |
| --- | --- |
| Account/OTP/login | auth-service: `AuthController`, `AuthService` |
| Sessions, invitations, username | auth-service: `SessionController`, `AdminInvitationService`, `UsernameService` |
| Profiles/Master List | user-service: `UserProfileController`, `SavedPassengerController` and their services |
| Catalogue validation | train-service: `TrainService`, `StationService` |
| Timetable validation | schedule-service: `ScheduleService` |
| Quotes/fare/date rules | reservation-service: `JourneyService` |
| Connections/nearby search | reservation-service: `JourneyPlanner` |
| Holds, seats, WL, cancellation | reservation-service: `BookingEngine`, `InventoryInitializer`, `ReservationJobs` |
| Dated run cancellation | reservation-service: `RunCancellationService`, `RunCancellationController`, `TrainRunCancellation`, `BookingEngine` |
| PDF/berth descriptions | reservation-service: `TicketPdfService`, `SeatLayouts` |
| In-app inbox/email outbox handoff | reservation-service: `NotificationCenter`, `ReservationJobs`, `NotificationRelay` |
| Orders/refunds/reconciliation | payment-service: `PaymentController`, `PaymentService` and scheduled jobs |
| Booking email delivery | notification-service: `NotificationInbox`, `NotificationJobs`, `BookingEmailTemplate` |
| Reports/user management | reservation-service: `AdminBookingService`; auth-service: `AdminUserService` |
| Assistant | chatbot-service: `ChatService`, `KnowledgeBase`, `RailwayTools`, `Conversations`, `SemanticCache`, `Guardrails`, `GuardrailAdvisor` |
| Dependency circuit breakers | reservation-service: `PnrGateway`; chatbot-service: `ChatService` |
| Shared Swagger lifecycle | api-gateway: `SwaggerSessionController` and `src/main/resources/swagger/session-lifecycle.js` |

## Common problems

| Problem | First checks |
| --- | --- |
| Service cannot get configuration | Config Server running; environment credentials loaded into that JVM |
| Eureka/Config page returns 401 | Use the infrastructure credentials rather than a passenger login |
| Protected API fails while token looks valid | Auth-service availability, revocation, account status and role |
| Google `redirect_uri_mismatch` | Register the exact gateway callback URI in the matching Google client |
| No train found | Station codes/order, source weekday, boarding day offset, class configuration, timetable and run cancellation |
| Timetable POST returns 400 | Read `fieldErrors`/message; check endpoint times, sequences, chronology, distance and catalogue endpoints |
| Booking mail missing | Event outbox, broker/HTTP handoff, notification inbox/delivery attempts and SMTP configuration |
| Refund pending | Payment-service reconciliation/provider state; do not interpret pending as processed |
| Assistant unavailable | `/api/chat/status`, model/key availability, RAG state, timeouts and downstream services |
| PDF font missing on a new laptop | Remove obsolete machine-specific override; use bundled font with blank `PDF_FONT_PATH` |

For a repeatable feature demonstration, use the [dataset walkthrough](../Railwize-india-data/INDIA-DATA-GUIDE.txt) with fresh future dates and disposable test accounts.

For wider route coverage, [ADD-MAJOR-CITY-ROUTES.sql](../Railwize-india-data/ADD-MAJOR-CITY-ROUTES.sql) provides 68 daily services with complete operating days, class/fare configurations and 414 scheduled stops. It can run independently or after the original catalogue. See the [major-city guide](../Railwize-india-data/MAJOR-CITIES-GUIDE.txt) for import instructions, complete schedules and Mumbai–Delhi troubleshooting. It does not insert booking inventory or modify existing published services.

## Passenger support requests

I added a private support workflow to the existing reservation service. Passengers enter through Help → Contact support or the support link on a booking. Administrators use `/admin/support`.

- Categories: Booking, Payment/refund, Account and Other. Subject is limited to 160 characters, messages to 3,000. No attachments.
- A PNR is optional and must belong to the account creating the request, including for an ADMIN creating a personal request.
- Owner-scoped passenger APIs use `/api/reservations/support`; ADMIN APIs use `/api/reservations/admin/support`. Both expose paged lists and paged conversations, with sizes 1–50.
- Create/reply bodies contain a UUID `requestId`; identical retries reuse it, while reuse with different content conflicts. Status changes carry the current ticket `version` and reject stale updates.
- States are OPEN, IN_PROGRESS and RESOLVED. A passenger reply sets OPEN; an admin reply sets IN_PROGRESS. Status changes are retained as conversation entries. Ticket-row locks coordinate replies/status changes.
- Search matches reference/subject/PNR, and also owner/email in the admin inbox. Lists show latest updates first; conversation page 0 contains the latest messages.
- Creation, admin replies and status changes produce owner in-app notifications and branded emails. An optional `SUPPORT_EMAIL` shared mailbox receives alerts for new requests/passenger replies. The admin inbox remains available without this mailbox.
- Events reuse the durable reservation outbox and notification inbox. Full conversation bodies stay behind authentication; email alerts link back to the appropriate owner/admin screen.
- A support request never cancels a ticket, processes a refund or changes an account automatically. Email replies are not ingested; users reply inside the application. Support is not a live chat or a guaranteed response-time service.

**Upgrade:** the two new tables are `support_tickets` and `support_messages` in `railway_reservation_db`. The notification tables gain nullable support references, and PNR becomes optional for non-booking notifications. With existing databases, stop reservation-service and notification-service and run [Enable-Support-Tickets.sql](Enable-Support-Tickets.sql), then restart them with Config Server serving the updated properties. The script is additive and repeatable, but MySQL DDL is not transactional; back up first. Fresh development schemas can be generated by the configured JPA schema update. No new database or service is needed.

Set optional `SUPPORT_EMAIL` in the private backend environment to a shared support mailbox. Existing SMTP settings deliver user support emails. An empty value disables admin mailbox alerts, not user email or the support inbox. Restart the reservation process after changing its environment.

Implementation: `SupportService`, `SupportController`, `AdminSupportController`, `SupportTicket`, `SupportMessage`, `NotificationCenter`, and notification-service's `SupportEmailTemplate`. `SupportIntegrationTest` covers access control, owned PNRs, retries, status conflicts, paging/filtering and durable events; `SupportEmailTest` covers no-PNR mail, escaping, links and duplicate delivery. Frontend Jasmine/Playwright scenarios cover submission references, notification routing and passenger/admin interactions.
