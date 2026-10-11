# Railwize — Angular railway reservation frontend

## Train cancellation and Help centre (11 October 2026)

- Admin → **Cancel a train run** (`/admin/train-runs`): choose a train number and original source-departure date, review affected bookings/passengers and full refund total, provide a reason, and acknowledge before submission. Cancellation history opens current processed/outstanding refund progress. Only future source departures are cancellable; there is no undo or train deletion. Dates use India time and include passengers boarding on later days of the same run. The server rechecks state when executing the action.
- Affected tickets show the operator reason and link to cancellation help. Paid fares receive full test refunds; unpaid bookings are cancelled, and late captures are reconciled. Separate onward tickets remain independent. Remove imported calendar reminders manually.
- **Help** is in the main navigation and at `/help`, with searchable answers, topic buttons, empty-result recovery and shortcuts to bookings, account and assistant. It covers train cancellation, payment recovery, refunds, waitlists, journey alternatives, fares, saved passengers and notifications. No fictional support contact or live-agent service is offered.
- Restart the updated reservation-service and notification-service, restart chatbot-service for its policy update, then restart Angular. See backend README for the additive schema upgrade. `tests/jasmine/help-runs.spec.ts` checks topic links/search and cancellation review safeguards. Run `npm test`, `npm run build`, then the configured browser suite locally.

A separate Angular 21 application for the railway-reservation-system backend. The design uses ivory, forest green and warm orange, with an original SVG train illustration, responsive passenger pages and an administrator workspace.

## Start locally

1. Start MySQL, RabbitMQ and the backend services using the backend README and Run-Service.ps1. The API gateway must be available at http://localhost:8080.
2. Open PowerShell inside this frontend folder. Use Node.js 22.12 or later in the 22.x line, or a compatible Node 24 installation.
3. Run:

```powershell
.\Start-Frontend.ps1 -Install
```

Open **http://localhost:4200**. Keep the terminal running; Ctrl+C stops the frontend. Subsequent starts use `.\Start-Frontend.ps1`.

The launcher uses the npm bundled with Node, avoiding a broken global npm wrapper. With a working npm installation, the equivalent commands are:

```powershell
npm ci
npm start
```

If PowerShell blocks the local launcher, use the npm commands above. No Angular CLI global installation is required.

## Backend connection and credentials

All application requests use relative `/api` URLs. `proxy.conf.json` forwards them to http://localhost:8080 during Angular development. Restart the dev server after changing that file.

Keep database, RabbitMQ, SMTP, JWT, Google client secret and Razorpay secret values in the **backend .env**. There is no frontend .env and no secret belongs in Angular source code.

Check these entries in the backend .env and restart the affected backend services after changing them:

```dotenv
GOOGLE_REDIRECT_URI=http://localhost:8080/api/auth/google/callback
FRONTEND_CALLBACK_URI=http://localhost:4200/auth/callback
ADMIN_INVITATION_URI=http://localhost:4200/admin/accept-invitation
```

Register the exact GOOGLE_REDIRECT_URI with your Google web OAuth client. Use **localhost consistently**, including when opening the frontend, so the browser session cookie used by Google sign-in is retained. Google starts through `/api/auth/google/start`; the frontend exchanges the short-lived callback code with credentials/cookies.

Razorpay keys stay in the backend. The frontend receives only the public test key and order details from the create-order API. It rejects keys outside `rzp_test_` and modes other than TEST, opens the official checkout script, then sends the returned payment/order/signature to the backend. A browser callback alone never marks a ticket confirmed. Use Razorpay test payment methods; tickets are demonstration tickets and not valid for travel.

## Passenger features

- Responsive home, station suggestions, station swap and direct-train search.
- Trimmed and normalized station codes; identical stations rejected.
- India-based date validation: today through two calendar months, inclusive; 1–6 passengers.
- Backend fare quotes, availability and CNF/WL information.
- Registration, email verification/resend, password reset, Google sign-in and normal login.
- Profile creation/editing, logout and logout on all devices.
- Saved passenger master list with add/edit/remove and reuse during booking.
- Booking review and an idempotency key retained for an uncertain booking retry.
- Two-minute payment-hold countdown using the backend expiry time.
- Razorpay test checkout, payment/refund status and automatic ticket refresh.
- Paid whole-booking WL and automatic backend promotion to CNF; no RAC or partial passenger promotion.
- Booking history with pagination, sorting, booking-status and journey-date filters.
- PNR details, server-generated PDF download and whole-booking cancellation.
- Cancellation fee/refund preview checked again before cancellation. Changed estimates require another confirmation.
- Readable backend validation errors and request references.
- Booking guide explaining holds, waitlists, refunds and demo limitations.

The frontend displays backend decisions. Fare calculation, seat assignment, waitlist fairness, promotion, expiry, refund processing, ownership checks and email delivery remain backend responsibilities. Emails do not require a browser page to stay open.

## Administrator access

There is one login screen for everyone. Sign in with an existing ADMIN account and choose **Admin workspace**, or open http://localhost:4200/admin.

To create the first administrator, follow the backend README: set BOOTSTRAP_ADMIN_ENABLED and the bootstrap identity/password in the backend .env, start auth-service, then disable bootstrap. Public registration never offers a role selector.

The admin workspace supports:

- Station creation/editing and paginated/sorted catalogue views.
- Train creation, operating days, class capacities and fare settings.
- Editing the fields the backend permits; established route/capacity fields are locked.
- Publishing an ordered timetable with intermediate stops and overnight day numbers.
- Viewing a published timetable as read-only.
- PNR lookup using the backend-authorized booking endpoint.
- Sending, listing and revoking admin invitations.
- Accepting a single-use emailed invitation with the intended verified account, then signing in again to receive the new role.

Invitation tokens arrive in a URL fragment, are removed from the visible URL and retained only for the current browser session until accepted. Angular route guards improve navigation; the backend is the authority for access control.

## Suggested demonstration

1. Start all backend services and this frontend.
2. Log in as admin; create stations, a train with fares/capacity/operating days, and its timetable. The backend Seed-DemoCatalogue.ps1 is an alternative; consult its usage in the backend README.
3. Register a passenger, verify the emailed code and sign in.
4. Add a saved passenger, then search for a published train on an operating date.
5. Book using that passenger and complete test checkout within two minutes.
6. Open My journeys, filter history, open the PNR and download its PDF.
7. Use a small-capacity train to demonstrate paid WL, cancellation, promotion and refund updates. The entire WL booking must fit available seats.
8. Separately check actual Google consent, OTP emails and an admin invitation with your configured provider credentials.

Empty catalogues show an honest empty state. The running application has no fake station or payment fallback.

## Code map

| Area | Location |
| --- | --- |
| Routes and access guards | src/app/routes.ts and src/app/core/api.ts |
| HTTP requests, bearer tokens, refresh, logout, catalogue loading | src/app/core/api.ts |
| API response/request types | src/app/core/models.ts |
| Dates, validation and readable errors | src/app/core/utils.ts |
| Official Razorpay test checkout | src/app/core/checkout.ts |
| Navigation, demo banner and footer | src/app/app.ts |
| Reusable search and visual controls | src/app/shared/ |
| Search, booking and ticket pages | src/app/pages/search.ts, book.ts, ticket.ts |
| Authentication, profile and master list | src/app/pages/auth.ts, account.ts, passengers.ts |
| History, admin and help pages | src/app/pages/bookings.ts, admin.ts, help.ts |
| Responsive visual design | src/styles.css |
| Development gateway forwarding | proxy.conf.json |
| Rule and browser tests | tests/ |

Access and refresh tokens use sessionStorage, scoped to a browser tab/session. They are never included in URLs. An expired access token is refreshed before one retry; refresh failure clears the session. Google and admin-invitation callbacks use the corresponding backend flow.

The booking DTO uses the backend's legacy `birthPreference` property. The saved-passenger DTO uses `berthPreference`; the booking form maps between them.

## Build and tests

```powershell
.\Start-Frontend.ps1 -Action Build
.\Start-Frontend.ps1 -Action Test
.\Start-Frontend.ps1 -Action BrowserTests
```

Or use `npm run build`, `npm test`, and `npm run test:e2e`. Browser tests expect Microsoft Edge installed and start a dev server on port 4201. To use Playwright's Chromium instead, remove `channel: 'msedge'` from playwright.config.ts and install it with `npx playwright install chromium`.

Production output is `dist/railway/browser`. Configure the host to return index.html for Angular routes and reverse-proxy `/api/**` to the gateway. Development proxy settings are not bundled into production. Google callback and invitation URLs must match the deployed frontend origin.

### Username choice and ticket design (27 September 2026)

- Account settings offer one display/login username change, with lowercase normalization, conflict feedback and an explicit one-time-choice acknowledgement. Google accounts with generated names are sent here after login and can continue to their original destination without changing their name immediately.
- The frontend uses `displayUsername` for visible account labels, falling back to `username` for older sessions. The original session `username` remains the internal account key so existing booking retries and ownership continue to work.
- GET/PUT `/api/auth/username` are authenticated. A successful update changes the account/header immediately; refreshed and future login responses contain the chosen name. This does not add a password to a Google account.
- Ticket downloads use the backend's new Railwize PDF layout and bundled fonts. No additional frontend assets or provider keys are required.
- Angular AOT/template compilation and 11 rule tests passed. The browser suite contains 21 scenarios, including Google username choice and conflict feedback. A current browser run is not verified here because Windows blocks test-worker spawning; the normal CLI build also returns `spawn EPERM`.

### Timetable, saved-passenger and profile improvements (26 September 2026)

- Timetable endpoints are fixed to the selected train. Source arrival and destination departure are disabled and sent as null. Intermediate stops are inserted before the destination; only intermediate stops can be removed. Client validation explains invalid distances, day numbers and chronology before publication.
- Each booking passenger has a saved-passenger selector, with loading/empty/error states and retry. All list pages are loaded. A saved record cannot be selected for two passengers at once; edits in the booking form do not change the master list.
- Password and Google sign-in redirect ADMIN accounts to `/admin`. Existing passenger return URLs, logged-in login/register guards and the OTP-to-login flow are retained.
- Registered accounts can read an empty profile before entering their details. Optional null fields render as empty form controls. Save details to persist a profile.

### Latest verification (26 September 2026)

- Strict Angular AOT component/template compilation and the isolated linked verification bundle passed.
- Eleven rule tests passed using TypeScript transpilation and the in-process Node test runner.
- The browser suite now contains nineteen scenarios. Its latest run could not start a worker because Windows returned `spawn EPERM`; these updated browser scenarios have not been verified here.
- The standard Angular CLI production build was also blocked by subprocess `spawn EPERM`. Run the Build and BrowserTests commands above in your normal terminal.

### Earlier baseline verification (before the latest changes)

- Strict Angular AOT component/template compilation passed.
- Eight rule tests passed.
- Fourteen browser regression tests passed against controlled API responses.
- Desktop and narrow-screen layouts were visually inspected.

Browser checks cover station validation and responsive navigation, login return URLs, retained booking retry keys/bodies, payment verification and WL status, rejection of live checkout keys, changing cancellation estimates, history filters, token refresh/logout, role restrictions, catalogue editing constraints, API errors, saved-passenger CRUD, Google code exchange, invitation acceptance and timetable publication.

The standard Angular CLI production build was blocked in the execution environment by Windows ancestor-directory access restrictions (EPERM). Verification therefore used strict Angular compilation plus an isolated linked bundle. This is **not a claim that the normal production build passed**. Run the Build command above in your ordinary local terminal before deployment.

The browser suite intercepts backend/provider responses. It does not constitute a live end-to-end run with MySQL, RabbitMQ, Google or Razorpay. Complete the demonstration steps above against your running backend to validate configured external services.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Station suggestions or search unavailable | Gateway on 8080, service discovery and downstream services |
| No trains found | Timetable, station order, operating day, class capacity, date window and backend data |
| Google callback fails | Exact redirect URLs, localhost consistency, same browser session, unexpired code |
| Admin link absent | ADMIN role returned by login; sign in again after invitation acceptance |
| Checkout rejected | Backend test configuration and public key beginning rzp_test_ |
| Paid ticket still WL | Expected: payment and booking confirmation are separate states |
| Hold expired | Create a new booking; the server decides expiry even if the browser clock differs |
| npm reports missing npm-cli.js | Use Start-Frontend.ps1 to invoke Node's bundled npm |
| Deep link fails after production deployment | Configure SPA index.html fallback and API reverse proxy |

## Calendar reminders and recent searches

- On a future **confirmed** ticket, choose **Add to calendar** to download an `.ics` departure reminder. Open/import it in Apple Calendar, Outlook or Google Calendar. Google Calendar on the web supports file import through Settings → Import & export.
- The file uses the booking's exact departure timestamp, converted to UTC, so calendar apps display it in their selected timezone. It includes a one-hour display reminder, subject to the calendar app's notification settings. Arrival time is not included because the booking response does not provide it.
- The booking status is checked again before download. Waitlisted, unpaid, cancelled, expired and departed bookings cannot create new calendar reminders. Once a waitlisted booking becomes confirmed, the option appears on refresh.
- Calendar files are snapshots: cancellation does not delete an imported event. Remove it manually if plans change. Repeated imports may be handled differently by calendar apps; the file retains the same event UID for the PNR.
- Home and journey search forms retain the five most recent valid submitted searches per signed-in account in this browser (`railwize.recent-searches.v2.<immutable-account-subject>` in localStorage), including date, class and guest count. Identical searches move to the top. Logout/account switching clears the displayed list; signing in again restores only that account's list. Guests use temporary in-memory history. Old unowned v1 history is discarded. Passenger details and credentials are never stored in search history.
- Choose a recent route to restore its fields, then **Find trains** for current availability and fares. Past dates are cleared and require a new travel date. Individual remove and Clear all controls delete history; corrupt or unavailable browser storage does not block searching.

Calendar file formatting follows [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545), including escaped text, UTC timestamps and UTF-8 line folding.

Verification for these additions: all 17 frontend rule tests pass (including six calendar/history tests); strict Angular template/TypeScript compilation and the linked verification bundle pass. The local browser preview verified history persistence, correct date display, reuse, mobile layout and clearing across reloads. Three Playwright scenarios were added for history and calendar downloads, but the Playwright worker and standard Angular production build cannot spawn in the current sandbox (`EPERM`); run `npm run build` and `npm run test:e2e` locally to complete those checks. Live backend calendar download/import remains a manual check.

## In-app notifications

The navbar bell and `/notifications` page now show durable booking/payment/WL/refund updates from reservation-service. Polling runs every 5 seconds while signed in with a visible tab, refreshes after successful booking/payment/cancellation API actions, and retries more slowly after failures. Unread status persists in the backend; logout clears the frontend state. The compact panel shows three latest items; history supports pagination, All/Unread, and mark-all-read. Clicking an item marks it read and opens the booking, where current status/payment deadlines are checked.

Restart reservation-service and Angular. Default backend `DDL_AUTO=update` creates `user_notifications`; manual migration 003 is available in reservation-service for validate/none. There are no new frontend settings. Updates begin with new events after this deployment; old emails are not backfilled.

Implementation: `src/app/core/notifications.ts` handles fetching/session-safe polling; `notification-refresh.ts` triggers refresh after API mutations; `shared/notification-bell.ts` renders the navbar panel; `pages/notifications.ts` renders history. The three authenticated APIs are under `/api/reservations/notifications`, documented in the backend README and Reservation Service Swagger.

Validation: strict Angular compilation and the verification bundle pass; 17 existing rule tests pass; desktop/mobile fixture previews verified polling, read actions, filtering and recovery. `tests/browser/notifications.spec.ts` adds two repeatable browser scenarios. Standard `npm run build` and Playwright execution remain blocked in this tool environment by Windows `spawn EPERM`; run them locally, then verify a real booking changes the bell within the next five-second poll after the backend commits it.

## Railwize assistant

`/assistant` is a signed-in chat page, available through the floating **Ask Railwize** link. It uses the existing Angular application and same-origin `/api` proxy; no Gemini key belongs in frontend code. The backend's new `chatbot-service` runs on 8089 and is routed through the gateway on 8080.

Features: progressive guarded SSE replies, FAQ references, verified train and own-booking cards, normal booking navigation, Stop, and clear/new conversation. Conversation IDs are private to the authenticated account. The last 20 messages are kept in server memory for 24 hours and reset on chatbot-service restart. The browser keeps only the current conversation ID. PNR queries should use `PNR` followed by the ten-digit number; the server aliases it before sending context to Gemini. Never put passwords, OTPs or payment details in chat.

Setup, environment variables, ownership rules, free-tier limitations, architecture, Swagger examples and the manual checklist are in the original backend `README.md`, section **Railwize AI assistant**. Fill the backend root `.env` `GEMINI_API_KEY`, restart Config Server / reservation-service / API Gateway, start `./Run-Service.ps1 chatbot-service`, then restart Angular normally. No Docker, WSL or Redis is required.

Files: `src/app/core/chat.ts` (authenticated streaming client), `chat-stream.ts` (bounded SSE parser), `src/app/pages/chat.ts` (UI), `src/chat.css` (responsive layout), `tests/jasmine/chat-stream.spec.ts` (split UTF-8, malformed/truncated and oversized responses). Run `npm test` and `npm run build` locally. The restricted tool environment uses strict `ngc` compilation and a linked verification bundle because the standard build subprocess reports `spawn EPERM`.

## Account privacy and compact UI fixes (29 September 2026)

Recent searches now belong to the immutable signed-in account, with an immediate visible reset on logout/account changes. Old shared v1 history is removed, and guests have memory-only history. Display-username changes preserve the same account's search list. The historical browser tests now authenticate before testing persisted searches.

Personal details includes a read-only verified email for passengers and administrators. It comes from user-service `/me`; restart user-service to receive the new field. Notification previews now show three items in a 340px scrollable panel. Full history and five-second polling are unchanged. The assistant uses a narrower chat panel and a smaller scrolling conversation area, with responsive mobile limits.

The original backend also fixes booking-email handoff when RabbitMQ is unavailable and switches Gemini's unavailable model to `gemini-3.5-flash-lite`. Read the backend README's **Final issue fixes** section for restart steps, diagnostics and verification limits. No frontend provider keys are required.

Verification: 21 unit checks and strict Angular compilation/linked bundle passed. Desktop/mobile browser fixture checks verified compact panel bounds and the read-only profile field. Actual Gemini FAQ retrieval and live model function calling against synthetic train data passed in the backend. External mailbox delivery must still be checked after restarting the services; standard `npm run build` and `npm run test:e2e` must be run locally because of this tool environment's subprocess restriction.

Final browser account-switch check: an Alice search disappeared after logout, Bob saw no recent searches, and signing back into Alice restored only Alice's saved route. The frontend production build was attempted again and reported the same Windows spawn EPERM restriction.
## Connecting trains and nearby stations (30 September 2026)

The journey search page now includes **More ways to get there**. Alternatives load automatically if no exact direct train has CNF availability; otherwise use **Find alternatives**. Controls include nearby stations, a 25/50/100 km radius and a minimum same-station transfer of 1, 1½ or 2 hours (up to 6 hours maximum).

Cards show actual boarding/arrival stations, distances from the requested stations, each train's date/time, transfer duration, individual and total fares, and CNF/WL availability. Local transport is excluded from fares and straight-line distance estimates. Review the itinerary and acknowledge the actual stations/independent-ticket conditions before booking. Each leg uses its own boarding date and ordinary booking/payment flow. **Return to journey options** on the ticket page restores the original search for booking the onward leg; it does not reserve that leg. No single-payment/composite-ticket guarantee is made.

Admin station create/edit now accepts optional latitude and longitude. Both must be supplied together within valid ranges. Nearby matching uses these values; exact-station connections still work when coordinates are missing. Restart the original backend's train-service and reservation-service, then restart Angular. The backend README documents the optional SQL migration, synthetic demonstration data, API parameters and search bounds. No extra frontend environment keys, map services or dependencies are needed.

Implementation: `shared/journey-alternatives.ts`, `core/journey-link.ts`, `pages/search.ts`, `pages/admin.ts`, `pages/book.ts`, `pages/ticket.ts` and the existing styles. Tests include malformed return context and `tests/browser/planner.spec.ts` for automatic suggestions, station/date links, review acknowledgement and narrow-screen overflow. Strict Angular compilation and the linked bundle passed; 23 unit checks passed. Browser checks using synthetic API fixtures verified desktop and 390px mobile layouts without horizontal overflow, review acknowledgement and the next-day second-leg booking link. A live two-payment checkout was not exercised. Standard production/Playwright commands must still be run locally when the tool environment cannot spawn their subprocesses.


## Assistant panel and account questions (1 October 2026)

Ask Railwize now opens a right-side panel on the current page; its expand link opens `/assistant`. Shared `core/chat-session.ts` keeps the conversation/draft/stream across the two views and clears visible state on logout or account changes. The full assistant page uses the site's usual content width and larger typography; other page styling is unchanged. Close with the close button, launcher or Escape.

Train cards require an explicit 1-6 passenger choice before navigating to booking. The selected count is validated by `core/chat-booking.ts`; the booking screen requotes it. Booking cards display departure time in IST. Quick prompts cover booking history, cancellations, counts and the next journey without asking for a PNR. The backend must include the updated reservation overview endpoint and chatbot account tool.

Restart reservation-service, chatbot-service and Angular together. No schema changes or frontend configuration changes are needed. 25 unit checks and Angular AOT/template compilation passed. Desktop/mobile browser checks using synthetic API responses verified panel navigation, conversation continuity and selected passenger count. Browser regression source is `tests/browser/assistant-panel.spec.ts`. The regular production build remains blocked in this tool environment by Windows spawn EPERM; run `npm run build` locally. Live Gemini/provider and payment operations were not part of these checks.

## Coach and berth details (1 October 2026)

Ticket passengers now show a class-specific coach badge, numbered berth/seat, full berth type and bay when available. The server supplies `seatDetails`; Angular never guesses the layout from a raw seat ID. Sleeper uses S, 3A B, 2A A, 1A H, 3E M, Executive E and Second Sitting D. For example, S1 / Berth 7 / Side lower berth replaces SL-C1-7. First AC and Economy have no guessed berth type; missing metadata and old IDs retain a readable fallback. WL remains unassigned until confirmation.

The reusable `shared/seat-assignment.ts` component is used in `pages/ticket.ts`; types are in `core/models.ts`. Existing capacity, seat allocation and recorded preferences are unchanged. Restart Angular with the updated reservation-service and notification-service. No schema or environment changes are needed. The backend README documents fixed layout patterns, real-world limitations and reference sources. PDFs and new booking/promotion emails receive matching descriptions.

Validation: strict Angular templates/AOT, linked verification bundle and 25 existing unit checks passed. Backend reservation and notification packages passed 105 and 15 tests respectively. Live email/payment operations were not repeated.

Desktop and 390px mobile browser checks with six synthetic passengers verified coach badges, berth labels and bay changes without page-wide overflow. The regular production build was attempted again and remains blocked by Windows `spawn EPERM`; run `npm run build` locally before deployment.

## Admin management and analytics (1 October 2026)

The main `/admin` workspace now includes:

- Overview with account/train/booking/cancellation counts, booking-status charts, popular trains and test booking fare/refund totals.
- `/admin/users`: search, role/status filters, sorting, pagination, personal details with email, booking history, reviewed Block/Reactivate actions and CSV export. Administrator accounts are protected; access still comes through invitations. User-history links use the original account key even after a display-username change.
- `/admin/bookings`: PNR/account/email search, exact account/train filters, journey dates, status, sorting, pagination and booking/cancellation CSVs. View / manage uses the existing ticket, PDF and cancellation-review flow and returns to Admin bookings. Admin view does not offer passenger checkout.
- `/admin/reports`: journey-date range and train filters, grouping by day/month/train, fare/refund and cancellation-rate charts, detailed table and Excel-compatible CSV.

Reports are grouped by journey date, not transaction date. Net subtracts completed and outstanding refunds; paid WL fares remain included. These are test booking reports, not a Razorpay settlement ledger. The backend README defines the accounting, exclusions, protected account rules, export limits and verification checklist. CSV is UTF-8 with spreadsheet-formula protection; native XLSX is not generated. Export buttons use the last applied filters.

Restart **auth-service**, **reservation-service** and Angular. No schema/environment changes or new dependencies are required. Existing user-service supplies personal details and train-service supplies catalogue counts. All new APIs enforce ADMIN on the backend as well as Angular route guards.

Files: `pages/admin-insights.ts`, `pages/admin-management.ts`, `core/admin-models.ts`, existing `core/api.ts`, `pages/admin.ts`, `pages/ticket.ts` and `routes.ts`. Browser regression scenarios: `tests/browser/admin.spec.ts`.

Checks: strict Angular compilation, linked browser bundle and 25 existing unit tests passed. Auth/reservation Maven packages passed 52/112 tests. Synthetic desktop browser checks covered dashboard, user details, reviewed block/status refresh, stable-account booking history and reports; mobile report layout was checked at 390px with no page-wide overflow. The embedded browser did not produce a CSV file-save event, so verify the final download in your local browser; backend CSV contents and authorization tests passed. Production build remains blocked in this environment by Windows spawn EPERM. Run `npm run build` and `npm run test:e2e` locally before deployment. No live accounts, booking cancellations, SMTP messages or payments were changed during verification.

## Jasmine unit tests (1 October 2026)

`npm test` now runs **Jasmine**, with TypeScript type checking before execution. The previous 25 Node-runner regression checks have moved to `tests/jasmine/*.spec.ts`; nine added specs cover authentication/authorization guards, logout cleanup, concurrent token refresh and logout races, bearer-token isolation, retry after HTTP 401, catalogue pagination, notification refresh, and refusing live Razorpay checkout. Total: **34 passing specs**.

```powershell
npm ci
npm test
# Equivalent launcher; now runs the entire Jasmine suite:
.\Start-Frontend.ps1 -Action Test
# Reproduce a randomized order when investigating a failure:
$env:JASMINE_SEED = '88752'
npm test
Remove-Item Env:JASMINE_SEED
```

Configuration: `tsconfig.spec.json`; runner: `scripts/test-jasmine.cjs`. The runner uses the installed TypeScript compiler and Jasmine in one Node process, so these unit tests do not need a browser, Karma, backend services or provider credentials. Angular services and guards run with real Angular dependency injection and mocked HTTP/router/storage boundaries. Generated test code is kept under ignored `.verify/jasmine`. Only specs still present in the source tree are run. Prefer Node 22.13+ for the tested setup.

These tests check logic and service behavior, not rendered layout or a complete booking across running services. Existing Playwright browser scenarios remain under `tests/browser` and run separately with `npm run test:e2e`. No SonarQube setup has been added.

