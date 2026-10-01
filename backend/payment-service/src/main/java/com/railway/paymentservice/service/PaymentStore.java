package com.railway.paymentservice.service;

import com.railway.paymentservice.dto.ReservationPaymentContext;
import com.railway.paymentservice.entity.*;
import com.railway.paymentservice.exception.PaymentException;
import com.railway.paymentservice.provider.PaymentProvider;
import com.railway.paymentservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Short database transactions only. Network calls belong to PaymentService. */
@Service @RequiredArgsConstructor
public class PaymentStore {
    private final PaymentRepository records;
    private final PaymentCaptureRepository captures;
    private final PaymentWebhookRepository webhooks;

    @Transactional
    public PaymentRecord initialize(ReservationPaymentContext context, Instant now) {
        return records.findByPnr(context.pnr()).orElseGet(() -> records.saveAndFlush(PaymentRecord.builder()
                .pnr(context.pnr()).username(context.username()).amount(context.totalFare()).currency(context.currency())
                .receipt("rail_" + UUID.randomUUID().toString().replace("-", ""))
                .status(PaymentRecord.PaymentStatus.INITIATING).holdExpiresAt(context.holdExpiresAt())
                .createdAt(now).updatedAt(now).nextReconcileAt(now).build()));
    }
    @Transactional
    public Optional<PaymentRecord> claimOrder(Long id, Instant now) {
        PaymentRecord record = records.lockById(id).orElseThrow();
        if (record.getRazorpayOrderId() != null) return Optional.of(record);
        if (record.getOrderLeaseUntil() != null && record.getOrderLeaseUntil().isAfter(now)) return Optional.empty();
        record.setOrderLeaseUntil(now.plusSeconds(60));
        record.setNextReconcileAt(now.plusSeconds(60));
        return Optional.of(record);
    }
    @Transactional
    public PaymentRecord orderCreated(Long id, String orderId, Instant now) {
        PaymentRecord record = records.lockById(id).orElseThrow();
        if (record.getRazorpayOrderId() != null && !record.getRazorpayOrderId().equals(orderId))
            throw new PaymentException(HttpStatus.CONFLICT, "Order is already associated with another provider order");
        record.setRazorpayOrderId(orderId);
        record.setStatus(PaymentRecord.PaymentStatus.CREATED);
        record.setOrderLeaseUntil(null);
        record.setUpdatedAt(now);
        record.setNextReconcileAt(now);
        return record;
    }
    @Transactional
    public PaymentCapture captured(PaymentRecord order, PaymentProvider.Payment payment, Instant now) {
        PaymentRecord locked = records.lockById(order.getId()).orElseThrow();
        var existing = captures.findById(payment.id());
        if (existing.isPresent()) {
            if (!existing.get().getOrderId().equals(order.getRazorpayOrderId()))
                throw new PaymentException(HttpStatus.CONFLICT, "Payment belongs to another order");
            return existing.get();
        }
        if (locked.getStatus() != PaymentRecord.PaymentStatus.SUCCESS)
            locked.setStatus(PaymentRecord.PaymentStatus.CAPTURED);
        locked.setUpdatedAt(now);
        locked.setNextReconcileAt(now);
        return captures.saveAndFlush(PaymentCapture.builder().paymentId(payment.id()).orderId(payment.orderId())
                .pnr(order.getPnr()).amount(order.getAmount()).currency(order.getCurrency())
                .refundStatus("NONE").createdAt(now).updatedAt(now).build());
    }
    @Transactional
    public void confirmed(Long orderId, String paymentId, Instant now) {
        PaymentRecord record = records.lockById(orderId).orElseThrow();
        PaymentCapture capture = captures.lockById(paymentId).orElseThrow();
        capture.setConfirmed(true);
        capture.setUpdatedAt(now);
        record.setStatus(PaymentRecord.PaymentStatus.SUCCESS);
        record.setRazorpayPaymentId(paymentId);
        record.setUpdatedAt(now);
    }
    @Transactional
    public PaymentCapture prepareRefund(String paymentId, BigDecimal amount, Instant now) {
        PaymentCapture capture = captures.lockById(paymentId).orElseThrow();
        if (amount == null || amount.signum() <= 0 || amount.compareTo(capture.getAmount()) > 0)
            throw new PaymentException(HttpStatus.CONFLICT, "Invalid refund amount from reservation");
        if (capture.getRefundKey() == null) {
            capture.setRefundAmount(amount);
            capture.setRefundKey(UUID.randomUUID().toString());
            capture.setRefundStatus("PENDING");
            capture.setUpdatedAt(now);
        } else if (capture.getRefundAmount().compareTo(amount) != 0) {
            throw new PaymentException(HttpStatus.CONFLICT, "Refund amount changed after processing began; review is required");
        }
        return capture;
    }
    @Transactional
    public PaymentCapture refundUpdated(String paymentId, PaymentProvider.Refund refund, Instant now) {
        PaymentCapture capture = captures.lockById(paymentId).orElseThrow();
        if (capture.getRefundId() != null && !capture.getRefundId().equals(refund.id()))
            throw new PaymentException(HttpStatus.CONFLICT, "Refund already has a provider reference");
        // Out-of-order deliveries must never downgrade a processed refund.
        if (!"PROCESSED".equals(capture.getRefundStatus())) {
            capture.setRefundId(refund.id());
            capture.setRefundStatus(switch (refund.status()) {
                case "processed" -> "PROCESSED";
                case "failed" -> "FAILED";
                default -> "PENDING";
            });
            capture.setUpdatedAt(now);
        }
        return capture;
    }
    @Transactional
    public void nextReconciliation(Long id, Instant next) {
        records.lockById(id).ifPresent(record -> record.setNextReconcileAt(next));
    }
    @Transactional
    public void enqueueWebhook(String hash, String body, Instant now) {
        if (!webhooks.existsById(hash)) webhooks.saveAndFlush(PaymentWebhook.builder()
                .bodyHash(hash).body(body).receivedAt(now).nextAttemptAt(now).build());
    }
    @Transactional
    public void webhookAttempt(String hash, boolean processed, Instant next) {
        webhooks.findById(hash).ifPresent(event -> {
            event.setProcessed(processed || event.isProcessed());
            event.setNextAttemptAt(next);
        });
    }
}
