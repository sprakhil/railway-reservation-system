package com.railway.paymentservice.service;

import com.railway.paymentservice.dto.PaymentOrderReq;
import com.railway.paymentservice.dto.PaymentOrderRes;
import com.railway.paymentservice.dto.PaymentVerificationReq;
import com.railway.paymentservice.entity.PaymentRecord;
import com.railway.paymentservice.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Transactional
    public PaymentOrderRes createOrder(String username, PaymentOrderReq req) {
        try {
            RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            // Razorpay expects amount in paise (multiply by 100)
            BigDecimal amountInPaise = req.getAmount().multiply(new BigDecimal("100"));

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise.longValue());
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + req.getPnr());

            // 1. Call Razorpay API to generate Order
            Order razorpayOrder = razorpay.orders.create(orderRequest);
            String orderId = razorpayOrder.get("id");

            // 2. Save CREATED status to database
            PaymentRecord paymentRecord = PaymentRecord.builder()
                    .pnr(req.getPnr())
                    .username(username)
                    .razorpayOrderId(orderId)
                    .amount(req.getAmount())
                    .currency("INR")
                    .status(PaymentRecord.PaymentStatus.CREATED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            paymentRepository.save(paymentRecord);
            log.info("Razorpay Order Created: {} for PNR: {}", orderId, req.getPnr());

            return PaymentOrderRes.builder()
                    .razorpayOrderId(orderId)
                    .pnr(req.getPnr())
                    .amount(req.getAmount())
                    .currency("INR")
                    .status("CREATED")
                    .build();

        } catch (Exception e) {
            log.error("Failed to create Razorpay Order", e);
            throw new RuntimeException("Error creating payment order: " + e.getMessage());
        }
    }

    @Transactional
    public String verifyPayment(PaymentVerificationReq req) {
        try {
            PaymentRecord paymentRecord = paymentRepository.findByRazorpayOrderId(req.getRazorpayOrderId())
                    .orElseThrow(() -> new RuntimeException("Order ID not found"));

            // 1. Create a signature verification payload
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", req.getRazorpayOrderId());
            options.put("razorpay_payment_id", req.getRazorpayPaymentId());
            options.put("razorpay_signature", req.getRazorpaySignature());

            // 2. Cryptographically verify the signature using our Secret Key
            boolean isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);

            // 3. Update database
            if (isValid) {
                paymentRecord.setStatus(PaymentRecord.PaymentStatus.SUCCESS);
                paymentRecord.setRazorpayPaymentId(req.getRazorpayPaymentId());
                paymentRecord.setUpdatedAt(LocalDateTime.now());
                paymentRepository.save(paymentRecord);
                log.info("Payment verified successfully for Order ID: {}", req.getRazorpayOrderId());
                return "Payment Successful";
            } else {
                paymentRecord.setStatus(PaymentRecord.PaymentStatus.FAILED);
                paymentRecord.setUpdatedAt(LocalDateTime.now());
                paymentRepository.save(paymentRecord);
                log.warn("Payment signature verification failed for Order ID: {}", req.getRazorpayOrderId());
                throw new RuntimeException("Payment verification failed! Invalid signature.");
            }
        } catch (Exception e) {
            log.error("Error verifying payment", e);
            throw new RuntimeException("Error during payment verification: " + e.getMessage());
        }
    }
}