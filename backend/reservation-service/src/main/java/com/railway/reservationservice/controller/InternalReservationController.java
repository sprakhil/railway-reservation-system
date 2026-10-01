package com.railway.reservationservice.controller;

import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.exception.BookingException;
import com.railway.reservationservice.service.BookingEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController @RequestMapping("/internal/reservations") @RequiredArgsConstructor
public class InternalReservationController {
    private final BookingEngine engine;
    @Value("${railway.internal.token:}") private String internalToken;
    @GetMapping("/{pnr}/payment-context")
    public PaymentContext context(@PathVariable String pnr,@RequestHeader(value="X-Service-Token",required=false) String token) {
        verify(token); return engine.paymentContext(pnr);
    }
    @PostMapping("/{pnr}/payment-confirmation")
    public PaymentDecision confirm(@PathVariable String pnr,@RequestHeader(value="X-Service-Token",required=false) String token,@Valid @RequestBody PaymentConfirmation payment) {
        verify(token); return engine.confirm(pnr,payment);
    }
    @PostMapping("/{pnr}/refund-status")
    public void refund(@PathVariable String pnr,@RequestHeader(value="X-Service-Token",required=false) String token,@Valid @RequestBody RefundUpdate update) {
        verify(token); engine.refundStatus(pnr,update);
    }
    @PostMapping("/{pnr}/payment-failure")
    public void failed(@PathVariable String pnr,@RequestHeader(value="X-Service-Token",required=false) String token) {
        verify(token); engine.paymentFailed(pnr);
    }
    private void verify(String token) {
        if(internalToken==null || internalToken.length()<32 || token==null || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
            throw new BookingException(HttpStatus.FORBIDDEN,"Internal service authentication required");
    }
}
