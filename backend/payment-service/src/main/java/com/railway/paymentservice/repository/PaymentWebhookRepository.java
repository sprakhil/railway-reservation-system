package com.railway.paymentservice.repository;
import com.railway.paymentservice.entity.PaymentWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
public interface PaymentWebhookRepository extends JpaRepository<PaymentWebhook, String> {
    List<PaymentWebhook> findByProcessedFalseAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Instant now, Pageable page);
}
