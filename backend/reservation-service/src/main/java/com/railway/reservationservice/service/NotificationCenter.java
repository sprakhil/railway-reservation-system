package com.railway.reservationservice.service;

import com.railway.reservationservice.dto.PageResult;
import com.railway.reservationservice.entity.*;
import com.railway.reservationservice.repository.UserNotificationRepository;
import com.railway.reservationservice.exception.BookingException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;

@Service @RequiredArgsConstructor
public class NotificationCenter {
    private final UserNotificationRepository repository;
    private final Clock clock;
    public record Item(String id,String pnr,String type,String title,String message,Instant createdAt,Instant readAt,String supportTicketId) {}
    public record Inbox(PageResult<Item> notifications,long unreadCount) {}

    @Transactional(propagation=Propagation.MANDATORY)
    public void support(String id,String owner,String ticketId,String pnr,String type,String title,String message) {
        if(repository.existsById(id))return;
        var n=new UserNotification();n.setEventId(id);n.setUsername(owner);n.setSupportTicketId(ticketId);n.setPnr(pnr);
        n.setEventType(type);n.setTitle(title);n.setMessage(message);n.setCreatedAt(clock.instant());repository.save(n);
    }

    // Must commit or roll back with the booking transition that produced the event.
    @Transactional(propagation=Propagation.MANDATORY)
    public void publish(Reservation booking,String type,String message) {
        String id=booking.getPnr()+":"+type;
        if(repository.existsById(id)) return;
        var entry=new UserNotification();
        entry.setEventId(id); entry.setUsername(booking.getUsername()); entry.setPnr(booking.getPnr());
        entry.setEventType(type); entry.setCreatedAt(clock.instant());
        entry.setTitle(switch(type) {
            case "BOOKING_HELD" -> "Your booking is reserved. Complete payment";
            case "PAYMENT_RECEIVED" -> booking.isWaitlistRequested()?"Your waitlisted booking is paid":"Your ticket is confirmed";
            case "PROMOTED" -> "Good news! Your waitlist is confirmed";
            case "PAYMENT_FAILED" -> "Your payment attempt failed";
            case "PAYMENT_INCOMPLETE" -> "Your payment hold has expired";
            case "CANCELLED" -> "Your booking has been cancelled";
            case "TRAIN_CANCELLED" -> "Your train was cancelled by the operator";
            case "WAITLIST_EXPIRED" -> "Your waitlist deadline has passed";
            case "REFUND_INITIATED" -> "Your test refund is being processed";
            case "REFUND_COMPLETED" -> "Your test refund has been processed";
            case "REFUND_DELAYED" -> "Your test refund needs attention";
            default -> "Your booking has an update";
        });
        entry.setMessage(booking.getFromStation()+" → "+booking.getToStation()+" · Train "+booking.getTrainNumber()+" · "+booking.getDateOfJourney()+". "+message);
        repository.save(entry);
    }
    @Transactional(readOnly=true)
    public Inbox list(String owner,int page,int size,boolean unreadOnly) {
        if(page<0 || size<1 || size>100) throw BookingException.badRequest("Page must be non-negative and size must be 1-100");
        var paging=PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt","eventId"));
        var entries=unreadOnly?repository.findByUsernameAndReadAtIsNull(owner,paging):repository.findByUsername(owner,paging);
        return new Inbox(PageResult.of(entries.map(n->new Item(n.getEventId(),n.getPnr(),n.getEventType(),n.getTitle(),n.getMessage(),n.getCreatedAt(),n.getReadAt(),n.getSupportTicketId()))),repository.countByUsernameAndReadAtIsNull(owner));
    }
    @Transactional
    public void read(String owner,String id) {
        if(!repository.existsByEventIdAndUsername(id,owner)) throw new BookingException(HttpStatus.NOT_FOUND,"Notification not found");
        repository.markRead(id,owner,clock.instant());
    }
    @Transactional public void readAll(String owner){repository.markAllRead(owner,clock.instant());}
}
