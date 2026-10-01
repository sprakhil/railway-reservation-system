> Current additions and API changes: see [README.md](../README.md). Catalogue/history lists are now paginated; paid WL, sessions/logout, admin invitations and demo PDFs are included.

# Authentication and Angular integration

Use the API gateway for the browser's API calls. Configure `FRONTEND_ORIGINS`
on api-gateway as a comma-separated list of exact origins, defaulting to
`http://localhost:4200`. Wildcards are rejected for credentialed CORS.

All three services read the configuration service location from `CONFIG_SERVER_URL`
(default `http://localhost:8888`) and its credentials from `CONFIG_SERVER_USERNAME`
and `CONFIG_SERVER_PASSWORD`. No config-server password is embedded in the source.

## Google sign-in

Set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `GOOGLE_REDIRECT_URI`
(`http://localhost:8080/api/auth/google/callback` for local development).
Register that exact redirect URI in the Google OAuth web client. Set
`FRONTEND_CALLBACK_URI` to the Angular callback page, default
`http://localhost:4200/auth/callback`. The config server must use these same
environment placeholders instead of overriding them with the old Swagger redirect.

1. Navigate the browser to `GET /api/auth/google/start` on the gateway.
2. The backend creates a browser-session-bound state and nonce, valid for five minutes.
3. Google calls the backend callback. The backend verifies the token's signature,
   issuer, audience, expiry, email verification and nonce, then consumes the state.
4. The backend redirects to the configured Angular callback with `?code=...`.
   Access and refresh tokens never appear in this URL.
5. Angular immediately posts `{"code":"..."}` to `/api/auth/google/exchange`
   using `withCredentials: true`. The code works once, for 60 seconds, only in
   the browser session that started sign-in. The JSON response is the usual
   `accessToken`, `refreshToken`, `username`, and `role`. Remove the code from
   browser history after exchange. On rejected consent the callback contains
   `error=google_sign_in_cancelled` instead.

Use the same hostname consistently for local Angular and gateway (do not mix
`localhost` and `127.0.0.1`). For HTTPS set `SESSION_COOKIE_SECURE=true`.
The session cookie is HttpOnly and SameSite=Lax; deploy Angular and gateway
on the same site, and route the whole OAuth flow to the same auth-service
instance or add shared session storage before scaling auth-service.
The old `POST /api/auth/google` endpoint returns HTTP 410.

New Google accounts set both verification flags. Existing verified local Gmail
or Google Workspace accounts may link to the verified Google subject. Unverified
local accounts, mismatched existing subjects and non-authoritative third-party
email addresses are refused automatic linking; they must use their existing
password login. Google-only accounts do not silently gain a local password via
the password-reset endpoint.

## OTP and password reset

Registration and password-reset OTPs have separate purposes, are BCrypt-hashed
at rest, expire after five minutes and allow at most five incorrect attempts.
The failed-attempt count commits when an incorrect code is rejected. Issuance
has a 60-second cooldown per account and purpose. Codes are consumed once.
Successful password reset deletes the user's refresh tokens; already-issued
access JWTs remain valid until their configured short expiry.

Admin bootstrap is disabled by default. To create an initial admin explicitly,
set `BOOTSTRAP_ADMIN_ENABLED=true`, `BOOTSTRAP_ADMIN_USERNAME`,
`BOOTSTRAP_ADMIN_EMAIL`, and a strong `BOOTSTRAP_ADMIN_PASSWORD`, then disable
bootstrap after creation. There is no built-in default admin password.

## Schema and verification

For an existing database, review and manually apply
`database/upgrade-existing-auth.sql` before starting the upgraded auth service.
It adds OTP metadata, expires legacy plaintext OTPs, and enforces unique Google
subjects. The script is a one-time existing-schema upgrade, does not delete user
data, and is deliberately not automatically executed or enabled as Flyway.

Auth/user context tests use isolated H2 databases. OTP security tests exercise
real Spring transactions, repositories and BCrypt with mocked email delivery.
Google tests mock provider calls and check state, nonce, expiry, replay and safe
account linking. Gateway tests verify configured preflights and rejected origins.
No test sends email or calls Google. Actual Google sign-in requires the user's
configured OAuth client and a browser integration check.
