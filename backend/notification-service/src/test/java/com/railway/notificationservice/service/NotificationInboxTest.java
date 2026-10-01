package com.railway.notificationservice.service;
import com.railway.notificationservice.dto.NotificationEvent;
import com.railway.notificationservice.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;import jakarta.mail.internet.MimeMessage;import java.time.Instant;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
@SpringBootTest
class NotificationInboxTest {
 @Autowired NotificationInbox inbox;@Autowired NotificationDeliveryRepository deliveries;@MockitoBean JavaMailSender sender;
 @org.junit.jupiter.api.BeforeEach void mail(){lenient().when(sender.createMimeMessage()).thenAnswer(i->new MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));}
 private NotificationEvent event(String id){return NotificationEvent.builder().eventId(id).eventType("PROMOTED").message("Your whole waitlisted booking is now confirmed. Seats: SL-C1-1").pnr("1234567890").userEmail("demo@example.test").username("Demo").trainNumber("12345").dateOfJourney("2030-01-02").bookingJson("{\"status\":\"CONFIRMED\"}").ticketPdf("%PDF-fixture".getBytes()).build();}
 @Test void duplicateEventsArePersistedAndSentOnlyOnce(){var e=event("promotion-once");inbox.accept(e);assertTrue(deliveries.findById(e.getEventId()).orElseThrow().getPayloadJson().contains("ticketPdf"));inbox.accept(e);inbox.deliver(e.getEventId());inbox.deliver(e.getEventId());verify(sender,times(1)).send(any(MimeMessage.class));assertNotNull(deliveries.findById(e.getEventId()).orElseThrow().getSentAt());}
 @Test void transientFailureRemainsDurableThenRetriesSuccessfully(){var e=event("promotion-retry");inbox.accept(e);doThrow(new org.springframework.mail.MailSendException("Temporary SMTP failure")).doNothing().when(sender).send(any(MimeMessage.class));inbox.deliver(e.getEventId());var d=deliveries.findById(e.getEventId()).orElseThrow();assertNull(d.getSentAt());assertEquals(1,d.getAttempts());d.setNextAttemptAt(Instant.now().minusSeconds(1));deliveries.save(d);inbox.deliver(e.getEventId());assertNotNull(deliveries.findById(e.getEventId()).orElseThrow().getSentAt());verify(sender,times(2)).send(any(MimeMessage.class));}
 @Test void retryBudgetIsBounded(){var e=event("promotion-exhausted");inbox.accept(e);var d=deliveries.findById(e.getEventId()).orElseThrow();d.setAttempts(5);deliveries.save(d);inbox.deliver(e.getEventId());verifyNoInteractions(sender);assertNull(deliveries.findById(e.getEventId()).orElseThrow().getSentAt());}
}
