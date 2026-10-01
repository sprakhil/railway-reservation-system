package com.railway.paymentservice.repository;
import com.railway.paymentservice.entity.PaymentCapture;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PaymentCaptureRepository extends JpaRepository<PaymentCapture, String> {
    List<PaymentCapture> findByPnrOrderByCreatedAtAsc(String pnr);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PaymentCapture c where c.paymentId = :id")
    Optional<PaymentCapture> lockById(String id);
}
