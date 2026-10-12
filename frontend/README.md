# Railwize Frontend

I built the Railwize frontend in **Angular** to give passengers a clear booking flow and administrators a dedicated workspace in the same application. The interface covers train discovery, journey alternatives, passenger details, test checkout, ticket management, notifications, help and the Railwize assistant.

This folder contains the client application. The [backend](../backend/README.md) owns authentication, permissions, fares, seat inventory, payment verification and booking state.

## Stack and structure

- Angular 21.2.24 with standalone components and lazy-loaded routes.
- TypeScript 5.9, Angular signals and RxJS for application state and asynchronous work.
- Angular Forms and reusable validation helpers.
- Responsive CSS, shared cards/forms and dedicated assistant styling.
- Jasmine unit/service tests and Playwright browser scenarios.

```text
frontend/
├── src/
│   ├── main.ts                  # Bootstrap the Angular application
│   ├── index.html               # Host document
│   ├── styles.css               # Shared responsive design
│   ├── chat.css                 # Assistant presentation
│   └── app/
│       ├── app.ts               # Main shell, navigation and global UI
│       ├── routes.ts            # Routes, lazy loading and guards
│       ├── core/                # API, auth, state, models and feature helpers
│       ├── pages/               # Passenger and administrator screens
│       └── shared/              # Reusable search, notification and ticket controls
├── public/                      # Public static assets
├── tests/jasmine/               # Logic, service and guard tests
├── tests/browser/               # Browser interaction scenarios
├── scripts/test-jasmine.cjs      # TypeScript compilation and Jasmine runner
├── angular.json                 # Build, assets, styles and dev-server settings
├── proxy.conf.json              # Development /api forwarding to port 8080
├── playwright.config.ts         # Browser-test server and browser selection
├── tsconfig*.json               # TypeScript application/test configuration
├── package.json                 # Dependencies and npm commands
├── package-lock.json            # Locked dependency versions
└── Start-Frontend.ps1            # Optional PowerShell launcher
```

## Passenger experience

### Account creation and access

Passengers can register, verify an email OTP, resend a code, sign in with credentials or Google, and reset a local-account password. Successful registration verification directs the user to sign in. Login and registration routes redirect an already signed-in user instead of presenting another account-entry form.

The account page shows personal details and the verified email for passengers and administrators. An active verified account can change its display/login username **once**. This is useful after Google assigns an initial generated name. I chose a one-time change to allow personalization while keeping account identity easy to follow. The backend retains a stable internal account key so bookings and saved data keep their owner.

Current-session and all-session logout are available. Passenger navigation and administrator navigation depend on the authenticated role; administrators land in `/admin` after login. Passenger Google accounts needing username personalization may first see that account step.

### Searching and recent searches

The home and journey pages accept departure station, destination, boarding date, class and passenger count. Station suggestions use the backend catalogue. Search results show the train, times, fare and the server's CNF/WL availability suggestion.

The last **five** valid searches can be restored, removed individually or cleared. They are scoped to the signed-in account in this browser. Guest history stays in memory. Switching accounts immediately clears the visible list; another account's searches are not displayed. Restoring an old search does not reuse old availability, and past dates must be replaced.

### Connecting trains and nearby stations

**More ways to get there** displays itinerary alternatives. It loads automatically when no exact direct result has confirmed availability, and can also be opened manually.

- Choose whether to include nearby departure/destination stations.
- Select a nearby radius of 25, 50 or 100 km.
- Select a minimum interchange time of 1, 1.5 or 2 hours; the UI uses a six-hour maximum.
- Compare actual stations, boarding dates, travel/transfer time, class availability and per-leg/combined fares.
- Review and acknowledge the actual stations and independent-ticket conditions before booking.
- Use **Return to journey options** from a ticket to resume the original itinerary and book the onward leg.

Nearby distances are straight-line estimates. Local transport is excluded. Each leg uses its own booking, payment and PNR; reviewing an itinerary does not hold seats on either train. The API supports a wider transfer-time range than the current UI controls; see the backend guide.

### Saved passengers and booking

The Master List lets a passenger add, edit and remove saved travellers. The booking page offers those records as selections for faster entry, while still allowing manual passenger details. Saved age and preference should be reviewed before reuse.

The passenger count is explicit and limited to **1–6**. The booking page requests a fresh quote, displays the total and creates a hold with an idempotency key. It distinguishes a confirmed-seat allocation from a waitlist allocation before checkout. The server controls the two-minute hold deadline.

Saved passengers use `berthPreference`; the booking API retains the legacy property name `birthPreference`. The frontend maps between them. A completed booking stores a passenger snapshot, so editing the Master List does not rewrite an old ticket.

### Razorpay checkout and booking status

The UI opens Razorpay checkout with the backend's test order, amount and public test key. It rejects live-mode keys. A successful checkout is sent to the backend for verification; the browser does not mark a booking confirmed by itself.

A paid WL ticket remains waitlisted until promotion. The ticket page separates booking, payment and refund state and provides recovery/status refresh after an interrupted checkout. An expired hold requires a new booking. The frontend does not create substitute payments when the provider is unavailable.

### Tickets, coach details and history

Ticket pages show PNR, journey and passenger information, CNF/WL status, payment/refund state and download actions. The seat component renders backend-provided metadata, for example **S1 · Berth 7 · Side lower**, instead of exposing only an internal seat identifier.

Coach prefixes include S for sleeper, B for 3A, A for 2A, H for 1A, M for 3E, E for EC, D for 2S and GS for UR. Detailed berth types are supplied for the supported SL/3A and 2A layouts. WL seats remain unassigned; unknown or unsupported layout details use a readable fallback.

Booking history supports pagination, sorting and booking-status/journey-date filters. Ticket PDFs are downloaded from the authenticated backend and reflect the state at download time.

### Cancellation and calendar reminders

Passenger cancellation first shows the backend's fee/refund estimate and requires confirmation. It applies to the whole booking. If status changes during review, the estimate must be refreshed. Operator-cancelled tickets show the cancellation reason and full-refund information.

**Add to calendar** downloads an `.ics` file for a future confirmed journey. It uses the departure timestamp in UTC and includes a one-hour reminder. Calendar-app settings control the actual alert. An imported event is a snapshot: later ticket cancellation does not remove it automatically. The file does not invent an arrival time absent from the booking response.

### Notifications and Help

The navbar bell opens a compact preview with the latest three items. The `/notifications` page offers full paginated history, All/Unread filtering and read actions. Opening an item marks it read and navigates to its booking or support conversation.

Notifications refresh every **five seconds** while signed in with a visible tab, and after relevant successful booking/payment/cancellation actions. Failures use slower retries. Unread state is saved by the backend; logout removes the visible account state.

The public `/help` page has searchable, categorized guidance and shortcuts to account, booking and assistant pages. It explains payment holds, WL promotion, cancellations, refunds and train-run cancellation. Contact support opens `/support` for a private request, with an optional owned PNR, category, subject and message. Users can follow and reply to requests; replying reopens a resolved conversation. Requests and conversations refresh every ten seconds while the tab is visible. Help remains in the upper utility bar, without a duplicate main-navigation link.

### Ask Railwize

The **Ask Railwize** launcher opens a right-side chat panel without leaving the current page. Its expand link opens `/assistant`; both views share the conversation, draft and current response stream. The panel can be closed using its controls or Escape.

The assistant supports streamed replies, FAQ references, verified train/booking cards, stopping a reply and starting/clearing a conversation. Quick prompts include booking lists, cancelled tickets, booking counts and the next journey. Train cards ask for the passenger count before opening the booking form, which requotes the journey.

The frontend reads authenticated SSE events through a bounded streaming parser. It never contains the Gemini API key. The assistant is read-only; normal application screens handle bookings and cancellations. Conversation state is account-scoped and cleared from view on logout or account changes.

## Administrator workspace

| Route | Screen and operations |
| --- | --- |
| `/admin` | Dashboard totals, booking-status charts, popular trains and fare/refund summaries |
| `/admin/users` | Search/filter/sort users, profile details, booking history, reviewed block/reactivate actions, CSV export |
| `/admin/bookings` | Search/filter bookings and cancellations, view tickets, review cancellation and export CSV |
| `/admin/reports` | Journey-date/train filters; grouping by day/month/train; financial and cancellation charts/tables; CSV |
| `/admin/stations` | Create/edit station details and paired latitude/longitude |
| `/admin/trains` | Create/configure trains, operating days, seat classes, coach capacity and fare rules |
| `/admin/schedules` | Load and publish validated timetables |
| `/admin/train-runs` | Preview/cancel a dated train run; inspect audit history and refund progress |
| /admin/support | Search/filter support requests, open conversations, reply and update status; ticket-version checks prevent overwriting a newer update |
| /admin/invitations | Invite administrators and review/revoke invitations |

Timetable editing disables source arrival and destination departure. Payload preparation also sets them to `null`, assigns consecutive stop sequences, and checks chronology, midnight departure days and increasing cumulative kilometres. Published-route restrictions are enforced by the backend.

Dated cancellation requires the **original source departure date**, a reason and explicit acknowledgement of the preview. It affects active bookings for that run, across classes and segments, and triggers full test refunds for paid tickets. Other dates and independent onward bookings remain unchanged.

Admin booking view uses the existing ticket/PDF/cancellation flow with a return link to management; it does not offer passenger checkout. CSV exports use the last applied filters. Reports group by journey date and describe test booking fares/refunds, not provider settlements. Native XLSX export is not implemented.

Administrator access comes from backend bootstrap or a verified invitation. Angular guards improve navigation, but every protected admin operation is authorized again by the backend.

## Route reference

| Access | Routes |
| --- | --- |
| Public | `/`, `/journeys`, `/help` |
| Guest account pages | `/auth/login`, `/auth/register`, and the recovery/verification modes in the auth page |
| OAuth completion | `/auth/callback` |
| Signed-in passenger/account | `/book/:trainNumber`, `/bookings`, `/bookings/:pnr`, `/passengers`, `/account`, `/notifications`, `/assistant`, `/support`, `/support/:id` |
| Invitation acceptance | `/admin/accept-invitation` with backend-verified invitation/account checks |
| ADMIN | `/admin` and its workspace children |
| Error pages | `/forbidden` and the wildcard not-found route |

## API connection and browser storage

All browser API requests use `/api` through the gateway. During development, [proxy.conf.json](proxy.conf.json) forwards those requests to `http://localhost:8080`.

| Data | Location and lifecycle |
| --- | --- |
| Access/refresh tokens | `sessionStorage`, scoped to the browser session/tab; cleared on logout/session failure |
| Recent searches | Account-scoped `localStorage`; guest searches use memory; no passenger details or credentials |
| Conversation reference | Current conversation ID; conversation messages are managed by the backend and active UI state |
| Notification read state | Backend; client lists/counts reset on account changes |
| Gemini/SMTP/Razorpay secrets | Backend environment only |

The API layer coordinates token refresh, retries an authenticated request once where appropriate, and clears invalid sessions. Requests are constrained to the application's API boundary. API field errors and request references are presented to help diagnose failures without exposing stack traces.

Google sign-in starts at `/api/auth/google/start`. The backend redirects to `/auth/callback` with a short-lived exchange code; the frontend exchanges it using the original browser session. Access/refresh tokens are not passed in the URL. Use `localhost` consistently during local development.

## Run locally

1. Start the backend using the [backend guide](../backend/README.md).
2. Install Node.js compatible with `package.json`: `^20.19.0`, `^22.12.0` or `>=24.0.0`. The project's practical setup uses Node 22.13+ in the 22.x line.
3. Open CMD in this `frontend` directory and run:

```text
npm ci
npm start
```

Open **http://localhost:4200**. No PowerShell execution-policy change is needed for these commands. In PowerShell, use `npm.cmd` if execution policy blocks the `npm.ps1` shim. There is no frontend `.env` containing backend credentials.

Optional PowerShell launcher:

```powershell
.\Start-Frontend.ps1 -Install
```

Fresh databases have no train results until an administrator adds a complete catalogue/timetable or imports the [India dataset](../Railwize-india-data/INDIA-DATA-GUIDE.txt).

## Build and test

```text
npm test
npm run build
npm run test:e2e
```

| Command | Scope |
| --- | --- |
| `npm test` | Type-check and run Jasmine logic/service/guard tests without a browser or running backend |
| `npm run build` | Standard Angular production build, including template compilation and bundle budgets |
| `npm run test:e2e` | Playwright browser scenarios, with a separate development server on port 4201 |

Jasmine covers authentication/guards, refresh/logout races, API authorization, timetable normalization, recent-search isolation, calendar output, assistant parsing/passenger selection, test-only checkout and related feature rules. Tests use mocked HTTP/router/storage boundaries where appropriate.

Playwright uses **Microsoft Edge** by default. To use Playwright Chromium, install it with `npx playwright install chromium` and remove `channel: 'msedge'` from `playwright.config.ts`. Browser scenarios use controlled API/provider responses; they do not prove live MySQL, SMTP, Google, Gemini or Razorpay behavior.

Run the standard build and browser suite in an environment that permits child processes. A restricted Windows `spawn EPERM` error is a blocked check, not a passing production build. Strict compiler checks alone do not replace the complete build.

## Deploying the built frontend

The build output is **`dist/railway/browser`**. Serve those static files, return `index.html` for Angular deep links, and reverse-proxy `/api/**` to the backend gateway. The development proxy is not included in the production bundle.

Update backend CORS, frontend base/callback/invitation URLs and Google OAuth configuration for the deployed origin. Use HTTPS and the corresponding secure-cookie configuration. Do not place backend secrets in public assets or compiled JavaScript. SSE proxy buffering should be disabled for assistant streams.

## Where I implemented the main features

| Feature | Source |
| --- | --- |
| API calls, session lifecycle and route guards | [core/api.ts](src/app/core/api.ts) |
| Routing | [routes.ts](src/app/routes.ts) |
| Search UI and recent searches | [shared/search-form.ts](src/app/shared/search-form.ts), [core/recent-searches.ts](src/app/core/recent-searches.ts) |
| Journey alternative cards/context | [shared/journey-alternatives.ts](src/app/shared/journey-alternatives.ts), [core/journey-link.ts](src/app/core/journey-link.ts) |
| Booking and saved-passenger selection | [pages/book.ts](src/app/pages/book.ts), [pages/passengers.ts](src/app/pages/passengers.ts) |
| Test checkout | [core/checkout.ts](src/app/core/checkout.ts) |
| Ticket, cancellation, calendar | [pages/ticket.ts](src/app/pages/ticket.ts), [core/calendar.ts](src/app/core/calendar.ts) |
| Coach/berth labels | [shared/seat-assignment.ts](src/app/shared/seat-assignment.ts) |
| Notification polling and panel | [core/notifications.ts](src/app/core/notifications.ts), [shared/notification-bell.ts](src/app/shared/notification-bell.ts) |
| Assistant state and streaming | [core/chat-session.ts](src/app/core/chat-session.ts), [core/chat-stream.ts](src/app/core/chat-stream.ts), [pages/chat.ts](src/app/pages/chat.ts) |
| Admin catalogue and timetable | [pages/admin.ts](src/app/pages/admin.ts), [core/timetable.ts](src/app/core/timetable.ts) |
| Admin analytics/management | [pages/admin-insights.ts](src/app/pages/admin-insights.ts), [pages/admin-management.ts](src/app/pages/admin-management.ts) |
| Dated train cancellation | [pages/admin-runs.ts](src/app/pages/admin-runs.ts) |
| Help centre | [pages/help.ts](src/app/pages/help.ts) |
| Support inbox/conversations | [pages/support.ts](src/app/pages/support.ts), [core/support.ts](src/app/core/support.ts) |

## Troubleshooting

| Symptom | Check |
| --- | --- |
| No train results | Correct station codes/direction/class, timetable, operating day, capacity/fares, future date and run cancellation |
| APIs unavailable | Gateway on 8080, Eureka registration and downstream services |
| Google redirect mismatch | Exact backend callback registered in Google; frontend callback is a separate address |
| Admin workspace denied | ADMIN returned by backend login; invitation accepted with the correct verified account; fresh login after elevation |
| Paid ticket remains WL | Payment succeeded but seats have not yet become available for the whole booking |
| Notifications delayed | Signed-in/visible tab, backend event creation, polling response and service health |
| Assistant fallback | Backend `/api/chat/status`, model/key settings, provider limits or downstream availability |
| Deep link returns 404 after deployment | Configure SPA fallback and API reverse proxy |

For a complete demonstration, follow the [India dataset guide](../Railwize-india-data/INDIA-DATA-GUIDE.txt), then verify actual provider integrations with the configured backend.

The [major-city expansion guide](../Railwize-india-data/MAJOR-CITIES-GUIDE.txt) adds 68 daily services with SL/3A/2A. After importing its SQL, test **MMCT → NDLS** or **CSMT → NDLS** with a future boarding date. Select station suggestions explicitly: city labels do not make different terminals interchangeable. Nearby search covers alternate terminals when their coordinates are present.
