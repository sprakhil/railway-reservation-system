package com.railway.paymentservice.service;

import com.railway.paymentservice.client.ReservationClient;
import com.railway.paymentservice.dto.*;
import com.railway.paymentservice.entity.*;
import com.railway.paymentservice.exception.PaymentException;
import com.railway.paymentservice.provider.PaymentProvider;
import com.railway.paymentservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class PaymentService {
    private final PaymentRepository records;
    private final PaymentCaptureRepository captures;
    private final PaymentWebhookRepository webhooks;
    private final PaymentStore store;
    private final PaymentProvider provider;
    private final ReservationClient reservations;
    private final Clock clock;

    public PaymentOrderRes createOrder(String username, PaymentOrderReq req) {
        if (req.getAmount() != null) throw error(HttpStatus.BAD_REQUEST, "Do not supply amount; the booking fare is authoritative");
        provider.requireTestConfiguration();
        ReservationPaymentContext context = reservations.context(req.getPnr());
        owner(context.username(), username, false);
        payable(context);
        PaymentRecord record;
        try { record = store.initialize(context, clock.instant()); }
        catch (DataIntegrityViolationException race) {
            record = records.findByPnr(context.pnr()).orElseThrow(() -> error(HttpStatus.CONFLICT, "Order creation is in progress"));
        }
        if (record.getAmount().compareTo(context.totalFare()) != 0 || !record.getCurrency().equals(context.currency()))
            throw error(HttpStatus.CONFLICT, "Saved order does not match booking fare");
        record = ensureOrder(record, true);
        // Do not invite checkout after the hold elapsed while a provider request was in flight.
        payable(reservations.context(record.getPnr()));
        return PaymentOrderRes.builder().pnr(record.getPnr()).razorpayOrderId(record.getRazorpayOrderId())
                .amount(record.getAmount()).amountInPaise(paise(record.getAmount())).currency(record.getCurrency())
                .status(record.getStatus().name()).keyId(provider.publicKey()).holdExpiresAt(record.getHoldExpiresAt())
                .mode("TEST").build();
    }

    private PaymentRecord ensureOrder(PaymentRecord record, boolean allowCreate) {
        if (record.getRazorpayOrderId() != null) return record;
        PaymentRecord claimed = store.claimOrder(record.getId(), clock.instant())
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "Order creation is in progress; retry shortly"));
        if (claimed.getRazorpayOrderId() != null) return claimed;
        Optional<PaymentProvider.Order> existing = provider.findOrderByReceipt(claimed.getReceipt());
        PaymentProvider.Order order;
        if (existing.isPresent()) order = existing.get();
        else {
            if (!allowCreate) return claimed;
            payable(reservations.context(record.getPnr()));
            // Receipt is durable and unique at Razorpay. A timeout is recovered by receipt lookup.
            order = provider.createOrder(claimed.getReceipt(), paise(claimed.getAmount()), claimed.getCurrency());
        }
        validateOrder(claimed, order);
        return store.orderCreated(claimed.getId(), order.id(), clock.instant());
    }

    public PaymentStatusRes verifyPayment(String username, PaymentVerificationReq req) {
        PaymentRecord record = order(req.getRazorpayOrderId());
        owner(record.getUsername(), username, false);
        provider.verifyCheckout(record.getRazorpayOrderId(), req.getRazorpayPaymentId(), req.getRazorpaySignature());
        PaymentProvider.Payment payment = provider.fetchPayment(req.getRazorpayPaymentId());
        acceptPayment(record, provider.fetchOrder(record.getRazorpayOrderId()), payment);
        return status(username, false, record.getPnr());
    }

    public PaymentStatusRes status(String username, boolean admin, String pnr) {
        ReservationPaymentContext context = reservations.context(pnr);
        owner(context.username(), username, admin);
        PaymentRecord record = records.findByPnr(pnr).orElse(null);
        List<PaymentStatusRes.CaptureStatus> details = captures.findByPnrOrderByCreatedAtAsc(pnr).stream()
                .map(c -> new PaymentStatusRes.CaptureStatus(c.getPaymentId(), c.isConfirmed() && "CONFIRMED".equals(context.status()) && Objects.equals(c.getPaymentId(), context.razorpayPaymentId()),
                        c.getRefundAmount(), c.getRefundStatus(), c.getRefundId())).toList();
        return new PaymentStatusRes(pnr, record == null ? null : record.getRazorpayOrderId(), context.totalFare(),
                context.currency(), record == null ? "NOT_STARTED" : record.getStatus().name(),
                context.status(), context.holdExpiresAt(), "TEST", details);
    }

    /** Always fetch current provider state: delayed/failed events cannot reverse successful payments. */
    private void acceptPayment(PaymentRecord record, PaymentProvider.Order order, PaymentProvider.Payment payment) {
        validateOrder(record, order);
        validatePayment(record, payment);
        if ("authorized".equals(payment.status())) {
            // Test accounts may be configured for manual capture. Capture the verified saved amount,
            // then recheck the reservation: an intervening hold expiry results in a full test refund.
            payment = provider.capturePayment(payment.id(), paise(record.getAmount()), record.getCurrency());
            validatePayment(record, payment);
        }
        if ("failed".equals(payment.status()) && !payment.captured()) reservations.paymentFailed(record.getPnr());
        if (!payment.captured() || (!"captured".equals(payment.status()) && !"refunded".equals(payment.status())))
            return; // Failed attempts keep the order available for retries; never confirm.
        PaymentCapture capture = store.captured(record, payment, clock.instant());
        if (capture.getRefundKey() != null) { reconcileRefund(capture); return; }
        if (payment.amountRefunded() > 0) {
            // Out-of-band dashboard refunds require review, not accidental ticket confirmation or another refund.
            throw error(HttpStatus.CONFLICT, "Provider payment has an external refund; review is required");
        }
        PaymentConfirmationResult result = reservations.confirm(record.getPnr(),
                new PaymentConfirmation(record.getRazorpayOrderId(), payment.id(), record.getAmount(), record.getCurrency()));
        if (result.refundRequired()) {
            capture = store.prepareRefund(payment.id(), result.refundAmount(), clock.instant());
            reconcileRefund(capture);
        } else if (Set.of("CONFIRMED", "WAITING").contains(result.status())) {
            store.confirmed(record.getId(), payment.id(), clock.instant());
        } else throw error(HttpStatus.CONFLICT, "Reservation did not confirm the payment; reconciliation will retry");
    }

    private static void validatePayment(PaymentRecord record, PaymentProvider.Payment payment) {
        if (!Objects.equals(payment.orderId(), record.getRazorpayOrderId())
                || payment.amount() != paise(record.getAmount()) || !record.getCurrency().equals(payment.currency()))
            throw error(HttpStatus.BAD_REQUEST, "Provider payment does not match the saved order");
    }

    private void reconcileRefund(PaymentCapture capture) {
        if ("PROCESSED".equals(capture.getRefundStatus())) {
            publishRefund(capture);
            return;
        }
        PaymentProvider.Refund refund;
        if (capture.getRefundId() != null) refund = provider.fetchRefund(capture.getRefundId());
        else {
            // Lookup handles a successful POST whose response was lost; idempotency covers races.
            refund = provider.fetchRefunds(capture.getPaymentId()).stream()
                    .filter(r -> capture.getRefundKey().equals(r.receipt())).findFirst().orElse(null);
            if (refund == null) refund = provider.refund(capture.getPaymentId(),
                    paise(capture.getRefundAmount()), capture.getRefundKey());
        }
        if (!capture.getPaymentId().equals(refund.paymentId()) || refund.amount() != paise(capture.getRefundAmount())
                || !capture.getRefundKey().equals(refund.receipt()))
            throw error(HttpStatus.CONFLICT, "Provider refund does not match the saved refund request");
        publishRefund(store.refundUpdated(capture.getPaymentId(), refund, clock.instant()));
    }
    private void publishRefund(PaymentCapture capture) {
        reservations.refundStatus(capture.getPnr(), capture.getPaymentId(),
                capture.getRefundStatus(), capture.getRefundAmount());
    }

    public void webhook(byte[] body, String signature) {
        if (body.length > 131072) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook payload is too large");
        provider.verifyWebhook(body, signature);
        String json = new String(body, StandardCharsets.UTF_8);
        try { new JSONObject(json).getString("event"); }
        catch (JSONException ex) { throw error(HttpStatus.BAD_REQUEST, "Invalid webhook payload"); }
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        try { store.enqueueWebhook(hash, json, clock.instant()); }
        catch (DataIntegrityViolationException duplicate) { /* Durable inbox already contains this signed body. */ }
    }

    public void reconcileDue() {
        for (PaymentWebhook event : webhooks.findByProcessedFalseAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                clock.instant(), PageRequest.of(0, 50))) {
            try {
                reconcileEvent(new JSONObject(event.getBody()));
                store.webhookAttempt(event.getBodyHash(), true, clock.instant());
            } catch (Exception ex) {
                store.webhookAttempt(event.getBodyHash(), false, clock.instant().plusSeconds(30));
                log.warn("Test payment webhook reconciliation will retry (event {})", event.getBodyHash());
            }
        }
        for (PaymentRecord record : records.findByNextReconcileAtLessThanEqualOrderByNextReconcileAtAsc(
                clock.instant(), PageRequest.of(0, 50))) {
            try { reconcileOrder(record); }
            catch (Exception ex) { log.warn("Test payment reconciliation will retry (record {})", record.getId()); }
            finally { store.nextReconciliation(record.getId(), clock.instant().plusSeconds(30)); }
        }
    }
    private void reconcileEvent(JSONObject event) throws JSONException {
        JSONObject payload = event.optJSONObject("payload");
        if (payload == null) return;
        JSONObject paymentWrapper = payload.optJSONObject("payment");
        JSONObject orderWrapper = payload.optJSONObject("order");
        JSONObject refundWrapper = payload.optJSONObject("refund");
        if (paymentWrapper != null) {
            String paymentId = paymentWrapper.getJSONObject("entity").getString("id");
            PaymentProvider.Payment payment = provider.fetchPayment(paymentId);
            PaymentRecord record = order(payment.orderId());
            acceptPayment(record, provider.fetchOrder(record.getRazorpayOrderId()), payment);
        } else if (orderWrapper != null) {
            reconcileOrder(order(orderWrapper.getJSONObject("entity").getString("id")));
        } else if (refundWrapper != null) {
            String paymentId = refundWrapper.getJSONObject("entity").getString("payment_id");
            PaymentCapture capture = captures.findById(paymentId)
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Payment capture is not yet reconciled"));
            if (capture.getRefundKey() != null) reconcileRefund(capture);
        }
    }
    private void reconcileOrder(PaymentRecord record) {
        if (record.getRazorpayOrderId() == null) {
            var context = reservations.context(record.getPnr());
            boolean pending = "PENDING_PAYMENT".equals(context.status()) && context.holdExpiresAt() != null
                    && context.holdExpiresAt().isAfter(clock.instant());
            record = ensureOrder(record, pending);
            if (record.getRazorpayOrderId() == null) return;
        }
        PaymentProvider.Order order = provider.fetchOrder(record.getRazorpayOrderId());
        for (PaymentProvider.Payment payment : provider.fetchOrderPayments(record.getRazorpayOrderId()))
            acceptPayment(record, order, payment);
        // Also retry durable captures/refunds if the provider collection lags or a refund status callback was lost.
        for (PaymentCapture capture : captures.findByPnrOrderByCreatedAtAsc(record.getPnr())) {
            if (capture.getRefundKey() != null) reconcileRefund(capture);
            else acceptPayment(record, order, provider.fetchPayment(capture.getPaymentId()));
        }
    }

    private PaymentRecord order(String orderId) {
        return records.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Payment order not found"));
    }
    private void payable(ReservationPaymentContext context) {
        if (!"PENDING_PAYMENT".equals(context.status()) || context.holdExpiresAt() == null
                || !context.holdExpiresAt().isAfter(clock.instant()))
            throw error(HttpStatus.CONFLICT, "Booking is not awaiting payment or its seat hold has expired");
        if (!"INR".equals(context.currency()) || context.totalFare() == null || context.totalFare().signum() <= 0)
            throw error(HttpStatus.CONFLICT, "Booking does not have a valid INR fare");
        paise(context.totalFare());
    }
    private static void owner(String expected, String actual, boolean admin) {
        if (!admin && (actual == null || !actual.equals(expected)))
            throw error(HttpStatus.FORBIDDEN, "You cannot access this booking's payment");
    }
    private static void validateOrder(PaymentRecord record, PaymentProvider.Order order) {
        if ((record.getRazorpayOrderId() != null && !record.getRazorpayOrderId().equals(order.id()))
                || order.amount() != paise(record.getAmount()) || !record.getCurrency().equals(order.currency())
                || (record.getReceipt() != null && !record.getReceipt().equals(order.receipt())))
            throw error(HttpStatus.BAD_REQUEST, "Provider order does not match the saved booking fare");
    }
    static long paise(BigDecimal amount) {
        try {
            long value = amount.movePointRight(2).longValueExact();
            if (value <= 0) throw new ArithmeticException();
            return value;
        } catch (ArithmeticException ex) { throw error(HttpStatus.BAD_REQUEST, "Invalid payment amount"); }
    }
    private static PaymentException error(HttpStatus status, String message) { return new PaymentException(status, message); }
}
