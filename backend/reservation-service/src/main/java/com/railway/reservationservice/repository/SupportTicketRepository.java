package com.railway.reservationservice.repository;
import com.railway.reservationservice.entity.SupportTicket;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface SupportTicketRepository extends JpaRepository<SupportTicket,String>,JpaSpecificationExecutor<SupportTicket> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from SupportTicket t where t.id=:id")
    Optional<SupportTicket> lock(@Param("id") String id);
}
