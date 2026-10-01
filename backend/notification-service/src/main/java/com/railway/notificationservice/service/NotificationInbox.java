package com.railway.notificationservice.service;
import com.railway.notificationservice.dto.NotificationEvent; import com.railway.notificationservice.entity.NotificationDelivery; import com.railway.notificationservice.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.time.Instant;
@Service @RequiredArgsConstructor
public class NotificationInbox {
 private final NotificationDeliveryRepository deliveries; private final EmailService emails;
 @Transactional public void accept(NotificationEvent event) {
  String id=event.getEventId()==null ? event.getPnr()+":LEGACY_CONFIRMATION" : event.getEventId();
  if(id.length()>150 || event.getPnr()==null || event.getUserEmail()==null || event.getUserEmail().isBlank()) throw new IllegalArgumentException("Invalid notification event");
  if(deliveries.existsById(id)) return;
  NotificationDelivery d=new NotificationDelivery(); d.setEventId(id); d.setPnr(event.getPnr()); d.setUserEmail(event.getUserEmail()); d.setUsername(event.getUsername());
  d.setTrainNumber(event.getTrainNumber()); d.setDateOfJourney(event.getDateOfJourney()); d.setEventType(event.getEventType()); d.setMessage(event.getMessage()); d.setPayloadJson(new tools.jackson.databind.json.JsonMapper().writeValueAsString(event)); d.setNextAttemptAt(Instant.now()); deliveries.saveAndFlush(d);
 }
 @Transactional public void deliver(String id) {
  NotificationDelivery d=deliveries.lock(id).orElseThrow();
  if(d.getSentAt()!=null || d.getAttempts()>=5 || d.getNextAttemptAt().isAfter(Instant.now())) return;
  NotificationEvent event=d.getPayloadJson()!=null ? new tools.jackson.databind.json.JsonMapper().readValue(d.getPayloadJson(),NotificationEvent.class) : NotificationEvent.builder().eventId(id).eventType(d.getEventType()).message(d.getMessage()).pnr(d.getPnr()).userEmail(d.getUserEmail()).username(d.getUsername()).trainNumber(d.getTrainNumber()).dateOfJourney(d.getDateOfJourney()).build();
  d.setAttempts(d.getAttempts()+1);
  if(emails.processTicketConfirmation(event)) d.setSentAt(Instant.now());
  else d.setNextAttemptAt(Instant.now().plusSeconds(30L*(1L<<d.getAttempts())));
 }
}
