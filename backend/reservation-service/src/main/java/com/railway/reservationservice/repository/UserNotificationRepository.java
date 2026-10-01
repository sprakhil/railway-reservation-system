package com.railway.reservationservice.repository;

import com.railway.reservationservice.entity.UserNotification;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface UserNotificationRepository extends JpaRepository<UserNotification,String> {
    Page<UserNotification> findByUsername(String username, Pageable page);
    Page<UserNotification> findByUsernameAndReadAtIsNull(String username, Pageable page);
    long countByUsernameAndReadAtIsNull(String username);
    boolean existsByEventIdAndUsername(String eventId,String username);
    @Modifying @Query("update UserNotification n set n.readAt=:now where n.eventId=:id and n.username=:owner and n.readAt is null")
    int markRead(@Param("id") String id,@Param("owner") String owner,@Param("now") Instant now);
    @Modifying @Query("update UserNotification n set n.readAt=:now where n.username=:owner and n.readAt is null and n.createdAt<=:now")
    int markAllRead(@Param("owner") String owner,@Param("now") Instant now);
}
