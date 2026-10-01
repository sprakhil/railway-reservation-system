package com.railway.paymentservice.controller;
import com.railway.paymentservice.dto.*;
import com.railway.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/payments") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderRes> createOrder(@Valid @RequestBody PaymentOrderReq request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createOrder(authentication.getName(), request));
    }
    @PostMapping("/verify")
    public ResponseEntity<PaymentStatusRes> verifyPayment(@Valid @RequestBody PaymentVerificationReq request, Authentication authentication) {
        return ResponseEntity.ok(paymentService.verifyPayment(authentication.getName(), request));
    }
    @GetMapping("/{pnr}/status")
    public PaymentStatusRes status(@PathVariable String pnr, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return paymentService.status(authentication.getName(), admin, pnr);
    }
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> webhook(@RequestBody byte[] body,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.webhook(body, signature);
        return ResponseEntity.accepted().body(Map.of("status", "ACCEPTED", "mode", "TEST"));
    }
}
