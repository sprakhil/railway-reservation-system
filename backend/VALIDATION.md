# Backend validation - 24 September 2026

All **245 automated tests passed**, with zero failures, errors or skipped tests across all 11 services. The selected integration suites contain **104 checks**; they were included in this complete run. The suite now contains 10 additional Swagger checks compared with the earlier 235-test report.

| Module | Tests |
| --- | ---: |
| api-gateway | 7 |
| auth-service | 33 |
| config-server | 4 |
| eureka-server | 3 |
| notification-service | 9 |
| payment-service | 41 |
| pnr-service | 7 |
| reservation-service | 69 |
| schedule-service | 21 |
| train-service | 31 |
| user-service | 20 |
| **Total** | **245** |

## Final validation fixes

- Null passenger elements are rejected with a 400 field error before catalogue calls or database changes; service-level protection also prevents direct callers from dereferencing null entries.
- Booking `birthPreference` and saved-passenger `berthPreference` accept LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER, NONE, or null/omitted. Empty, lowercase and unsupported values are rejected.
- Station codes are stripped and uppercased in search, quote, booking fingerprints, catalogue input/lookup and timetable input. JSON DTO construction explicitly preserves normalization before catalogue validation. Canonical retries reuse one booking; normalized duplicate stations are rejected.
- New journeys are limited to today through today plus two calendar months inclusive, based on Asia/Kolkata. Tests cover month-end clamping, leap years, the exact first rejected day, India midnight, past dates and already-departed journeys.

## Swagger verification

All eight business services expose a generated project-specific OpenAPI document through real HTTP tests without credentials. Checks cover metadata, the common bearerAuth scheme, public-operation overrides, request examples, response-only schema fields and exclusion of internal/obsolete paths. Gateway tests use the production route/UI properties with local HTTP targets to verify eight dropdown entries, persistent authorization configuration, documentation routing, Swagger HTML delivery and bearer-header forwarding across services. These are HTTP/configuration checks; a browser authorization-persistence UI test was not run.

The README now contains database creation SQL, exact startup order, Swagger usage, request JSON for every body-taking public/admin endpoint, filters, provider-flow limitations and manual acceptance checks. No schema migration or business lifecycle changes were introduced by the Swagger update.

## Integration evidence

- `BookingHttpIntegrationTest`: 29 checks through a real random-port Spring Boot HTTP server, real JWT filtering, session-check HTTP calls, Feign train/schedule/PNR clients, business services and H2 repositories. Other services are local HTTP stubs and the clock is controlled. No ReservationService or BookingEngine mock is used. Scenarios include canonical search/quote/book/replay, all four validation fixes, invalid passenger counts, paid CNF/WL, promotion after cancellation, duplicate cancellation, refund callbacks, expiry/late captures, WL cutoff, authenticated internal endpoints, revoked sessions, ownership and PDF download.
- `PaymentFlowIntegrationTest`: real payment services, transaction/persistence logic and H2, with mocked Razorpay provider and reservation boundaries. Two new MockMvc flows cover saved-amount order creation, paid WL verification and duplicate callbacks, ownership, and rejection of client-supplied amounts. Existing signature/provider and payment recovery tests also pass.
- New catalogue/timetable/Master List MockMvc integration suites use real security/validation/controller/service/repository stacks and isolated H2 databases. They verify station normalization, uniqueness and admin restrictions; timetable publication and normalized replay/duplicate detection; supported preferences and owner-scoped passenger CRUD. The remote timetable catalogue boundary is mocked.
- Existing auth integration suites cover OTPs, sessions/logout and invitations; notification integration covers durable inbox deduplication and retries; H2 booking tests cover concurrent allocation, overlapping journeys and FIFO promotion. All other module regressions were rerun.

## How to repeat

From the backend root in PowerShell, run `./Verify-Backend.ps1` for all suites or `./Verify-Backend.ps1 -Integration` for the selected integration suites. The scripts use Maven and do not load `.env`. No provider credentials or running MySQL/RabbitMQ/Config Server/Eureka are required. The HTTP suite binds temporary loopback ports.

## Verification method and limits

The executed run used JDK 22 (source target Java 21), Maven 3.9.9, H2, local HTTP stubs and mocked external dependencies. No real payment, refund, email or railway ticket was issued. Google and Razorpay implementation remain configured for the existing Google sign-in and Razorpay TEST-only flows.

Normal Maven compilation encounters a Windows file-manager archive-cleanup AccessDeniedException in this restricted agent environment. The verification harness resolved each module's Maven classpath, compiled all main/test Java sources through JavaCompiler, copied application/test resources, and ran the real Surefire 3.5.6 suites. Compilation errors fail verification; only archive-close permission errors after successful compilation are tolerated. The delivered verification script uses the normal Maven lifecycle on the developer machine. This report does not claim that a normal unrestricted Maven lifecycle succeeded here.

These are backend/component integration tests, not a deployed end-to-end run of every live service. MySQL-specific behaviour, manual database upgrades, live RabbitMQ/SMTP, browser Google login and configured Razorpay test checkout still require deployment validation. Manual migrations have not been executed; the final validation fixes introduce no additional schema migration. Angular and the admin frontend are still separate pending work. Existing limitations (whole-booking FIFO, no RAC or partial cancellation, bounded email retries and possible SMTP send/commit duplication) remain documented in README.

The generated six-passenger PDF was reopened, text-checked and rendered by the passing PDF test; its layout had been visually inspected in the earlier run and PDF rendering code was unchanged in this update.

`verification-results.json` contains counts extracted from the latest Surefire reports. `source-sha256.csv` fingerprints delivered source/documentation files, excluding the manifest itself and private `.env`.
