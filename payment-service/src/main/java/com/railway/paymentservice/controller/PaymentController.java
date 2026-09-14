package com.railway.paymentservice.controller;

import com.railway.paymentservice.dto.PaymentOrderReq;
import com.railway.paymentservice.dto.PaymentOrderRes;
import com.railway.paymentservice.dto.PaymentVerificationReq;
import com.railway.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderRes> createOrder(
            @Valid @RequestBody PaymentOrderReq request,
            Authentication authentication) {
        String username = authentication.getName();
        return new ResponseEntity<>(paymentService.createOrder(username, request), HttpStatus.CREATED);
    }

    @PostMapping("/verify")
    public ResponseEntity<String> verifyPayment(@Valid @RequestBody PaymentVerificationReq request) {
        return ResponseEntity.ok(paymentService.verifyPayment(request));
    }
}