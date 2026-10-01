package com.railway.paymentservice.config;
import com.railway.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import java.time.Clock;
@Configuration @EnableScheduling
public class PaymentReconciliationConfig {
    @Bean Clock paymentClock() { return Clock.systemUTC(); }
    @Bean @ConditionalOnProperty(name = "railway.payment.reconciliation-enabled", havingValue = "true", matchIfMissing = true)
    Reconciler paymentReconciler(PaymentService service) { return new Reconciler(service); }
    @RequiredArgsConstructor
    static class Reconciler {
        private final PaymentService service;
        @Scheduled(fixedDelayString = "${railway.payment.reconciliation-delay-ms:15000}", initialDelayString = "${railway.payment.reconciliation-delay-ms:15000}")
        public void run() { service.reconcileDue(); }
    }
}
