> Current additions and API changes: see [README.md](../README.md). Catalogue/history lists are now paginated; paid WL, sessions/logout, admin invitations and demo PDFs are included.

# Payment backend integration

This service permits Razorpay TEST credentials only. It does not support live payments. No credentials are committed. Automated tests replace Razorpay and reservation calls with local mocks; running tests moves no money and sends no external payment requests.

## Configuration

Supply `RAZORPAY_TEST_KEY_ID` (`rzp_test_...`), `RAZORPAY_TEST_KEY_SECRET`, `RAZORPAY_TEST_WEBHOOK_SECRET`, and `RAILWAY_INTERNAL_TOKEN`. Use the same internal token in reservation-service. Set `RESERVATION_SERVICE_URL` if reservation-service does not run at `http://localhost:8085`. Existing database, JWT, discovery and config-server settings still apply. A nonempty key without the test prefix prevents startup. Missing credentials leave checkout unavailable (503), rather than pretending payment succeeded.

Configure the webhook in the Razorpay **test** dashboard to `/api/payments/webhook`, using the configured test webhook secret. Subscribe to `payment.captured`, `payment.failed`, `order.paid`, `refund.created`, `refund.processed`, and `refund.failed`. The endpoint verifies the original request bytes before enqueueing them durably. Polling reconciliation is also enabled every 15 seconds, so a lost callback or browser closure is recoverable. Reconciliation depends on payment-service being running.

## Angular contract

All public routes below require the passenger's Bearer JWT except the signed webhook:

1. Create a pending reservation using reservation-service. Preserve its PNR and server hold expiry.
2. `POST /api/payments/create-order` with `{"pnr":"1234567890"}`. Do not send an amount. The response includes `keyId`, `razorpayOrderId`, integer `amountInPaise`, `currency`, `mode: "TEST"` and `holdExpiresAt`. The fare comes from the saved reservation. Repeated requests reuse the same order. A 409 during order creation means retry shortly; it never extends the seat hold.
3. Start Razorpay Checkout with the returned test key, order ID, currency and integer paise amount. Clearly label checkout as a simulation. The server does not trust browser success alone.
4. `POST /api/payments/verify` with `razorpayOrderId`, `razorpayPaymentId`, `razorpaySignature`. Only the booking owner may verify. The signature and provider order, amount, currency and captured state are checked; manual-capture test payments are captured first. The reservation service atomically confirms or requests a refund if the hold already expired.
5. `GET /api/payments/{pnr}/status` returns payment, booking and per-capture refund statuses. The booking owner and an admin may read it. Poll after a timeout or browser return. Render a ticket only after booking status is `CONFIRMED`.

A failed attempt leaves checkout retryable while the reservation is payable. Cancellation rules and the authoritative refund amount come from reservation-service. A late or duplicate captured payment is fully refunded in test mode. Refund status is `NONE`, `PENDING`, `PROCESSED` or `FAILED`; `PROCESSED` never downgrades. Terminal provider failures and external dashboard refunds remain visible for manual test-data review, rather than inventing successful refunds.

## Persistence and migration

The implementation uses short database transactions with row locks and optimistic versions. No provider HTTP call is made inside a database transaction. Unique PNR/receipt constraints and a 60-second order-creation lease prevent duplicate checkout orders; an interrupted request is recovered using the durable receipt. Every captured payment is tracked independently, including an unexpected second capture. Refund amount and receipt are saved before sending the provider request. Razorpay treats the refund receipt as an idempotency key; after a lost response, the service finds that receipt before retrying. The webhook inbox deduplicates by the signed body's SHA-256 hash.

For a new MySQL database, run `db/migration/002_create_test_payment_tables.sql`, then use `spring.jpa.hibernate.ddl-auto=validate`.

For the original backend database, stop payment-service and back it up, run `001_archive_original_payments.sql` once, then `002_create_test_payment_tables.sql`. Original rows remain in `payment_records_legacy_20260917` for review. They lack trustworthy reservation fares/holds and may share PNRs, so they are deliberately not put into automatic capture/refund reconciliation. This is a manual one-time migration, not an auto-applied Flyway migration. Do not run the archive script against an already upgraded database. MySQL DDL is not transactional; inspect state before resuming an interrupted migration.

## Verification

`mvn test` runs H2-backed integration tests for amount/ownership/signature rejection, captured-only confirmation, hold expiry, duplicate callbacks/captures, concurrency, lost order/refund/confirmation responses, cancellation refunds, and signed webhook persistence. Provider adapter tests verify actual HMAC checks and mocked HTTP request contracts without contacting Razorpay. A real test-dashboard checkout requires the owner's test credentials; it is separate from the offline test suite.

Provider references: [checkout integration](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps/), [normal refund receipt and states](https://razorpay.com/docs/api/refunds/create-normal/), [order receipt lookup](https://github.com/razorpay/razorpay-node/blob/master/documents/order.md).
