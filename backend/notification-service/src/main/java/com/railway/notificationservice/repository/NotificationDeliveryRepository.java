package com.railway.notificationservice.repository;
import com.railway.notificationservice.entity.NotificationDelivery; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*; import java.time.Instant;
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery,String> {
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select d from NotificationDelivery d where d.eventId=:id") Optional<NotificationDelivery> lock(@Param("id") String id);
 List<NotificationDelivery> findTop50BySentAtIsNullAndAttemptsLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(int attempts,Instant now);
}
