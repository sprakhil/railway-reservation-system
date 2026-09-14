package com.railway.paymentservice.repository;

import com.railway.paymentservice.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentRecord, Long> {
    Optional<PaymentRecord> findByRazorpayOrderId(String razorpayOrderId);
}