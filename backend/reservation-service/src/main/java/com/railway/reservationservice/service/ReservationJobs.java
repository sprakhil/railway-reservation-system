package com.railway.reservationservice.service;

import com.railway.reservationservice.dto.NotificationEvent;
import com.railway.reservationservice.repository.BookingNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="booking.jobs.enabled",havingValue="true",matchIfMissing=true)
public class ReservationJobs {
    private final BookingEngine engine;
    private final TicketPdfService tickets;
    private final BookingNotificationRepository notifications;
    private final RabbitTemplate rabbit;
    private final Clock clock;
    private final NotificationRelay relay;
    @Value("${rabbitmq.exchange.name:railway_exchange}") private String exchange;
    @Value("${rabbitmq.routing.key:notification.email}") private String routingKey;
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void retryBacklogOnStartup(){notifications.retryPending(clock.instant());}

    @Scheduled(fixedDelayString="${booking.expiry-scan-ms:10000}")
    public void expire() { engine.expireHolds(); }

    @Scheduled(fixedDelayString="${booking.notification-scan-ms:30000}")
    public void deliverNotifications() {
        for(var pending:notifications.findTop50ByDeliveredAtIsNullAndNextAttemptAtLessThanEqual(clock.instant())) {
            try {
                NotificationEvent event=NotificationEvent.builder().pnr(pending.getPnr()).supportTicketId(pending.getSupportTicketId()).username(pending.getUsername())
                    .bookingJson(pending.getBookingJson()).eventId(pending.getEventId()).eventType(pending.getEventType()).message(pending.getMessage())
                    .userEmail(pending.getUserEmail()).trainNumber(pending.getTrainNumber()).dateOfJourney(pending.getDateOfJourney()).build();
                if(pending.getBookingJson()!=null && java.util.Set.of("PAYMENT_RECEIVED","PROMOTED").contains(pending.getEventType())) {
                    var snapshot=new tools.jackson.databind.json.JsonMapper().readValue(pending.getBookingJson(),com.railway.reservationservice.dto.ReservationRes.class);
                    event.setTicketPdf(tickets.render(snapshot));
                }
                CorrelationData correlation=new CorrelationData(pending.getEventId());
                try {
                    rabbit.convertAndSend(exchange,routingKey,event,correlation);
                    var confirmation=correlation.getFuture().get(5,TimeUnit.SECONDS);
                    if(!confirmation.isAck() || correlation.getReturned()!=null) throw new IllegalStateException("Notification not routed");
                } catch(InterruptedException interrupted) {throw interrupted;}
                catch(Exception brokerUnavailable) {relay.deliver(event);}
                pending.setDeliveredAt(clock.instant());
            } catch(Exception ex) {
                pending.setAttempts(pending.getAttempts()+1);
                pending.setNextAttemptAt(clock.instant().plusSeconds(Math.min(3600L,30L*(1L<<Math.min(6,pending.getAttempts())))));
                log.warn("Ticket email handoff deferred ({})",ex.getClass().getSimpleName());
                if(ex instanceof InterruptedException) Thread.currentThread().interrupt();
            }
            notifications.save(pending);
        }
    }
}
