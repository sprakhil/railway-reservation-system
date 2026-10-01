package com.railway.paymentservice.repository;
import com.railway.paymentservice.entity.PaymentRecord;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface PaymentRepository extends JpaRepository<PaymentRecord, Long> {
    Optional<PaymentRecord> findByRazorpayOrderId(String razorpayOrderId);
    Optional<PaymentRecord> findByPnr(String pnr);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentRecord p where p.id = :id")
    Optional<PaymentRecord> lockById(Long id);
    List<PaymentRecord> findByNextReconcileAtLessThanEqualOrderByNextReconcileAtAsc(Instant now, Pageable page);
}
