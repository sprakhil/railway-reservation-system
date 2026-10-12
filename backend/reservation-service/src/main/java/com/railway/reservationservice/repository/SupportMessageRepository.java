package com.railway.reservationservice.repository;
import com.railway.reservationservice.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface SupportMessageRepository extends JpaRepository<SupportMessage,String> {
    Page<SupportMessage> findByTicketId(String ticketId,Pageable page);
}
