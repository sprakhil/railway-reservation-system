package com.railway.reservationservice.service;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.entity.BookingNotification;
import com.railway.reservationservice.repository.BookingNotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ReservationEmailOutboxTest {
 @Test void supportNotificationPreservesReferenceWithoutBookingOrPdf() {
  var repo=mock(BookingNotificationRepository.class);var pdf=mock(TicketPdfService.class);
  var rabbit=mock(RabbitTemplate.class);var relay=mock(NotificationRelay.class);
  var pending=new BookingNotification();pending.setEventId("support-reply:user");
  pending.setEventType("SUPPORT_REPLY");pending.setSupportTicketId("SUP-"+UUID.randomUUID());
  pending.setUserEmail("alice@example.test");pending.setUsername("alice");
  when(repo.findTop50ByDeliveredAtIsNullAndNextAttemptAtLessThanEqual(any())).thenReturn(List.of(pending));
  var jobs=new ReservationJobs(mock(BookingEngine.class),pdf,repo,rabbit,Clock.systemUTC(),relay);
  ReflectionTestUtils.setField(jobs,"exchange","railway_exchange");ReflectionTestUtils.setField(jobs,"routingKey","notification.email");
  doAnswer(invocation->{
   NotificationEvent event=invocation.getArgument(2);
   assertEquals(pending.getSupportTicketId(),event.getSupportTicketId());
   assertEquals("alice@example.test",event.getUserEmail());assertNull(event.getPnr());assertNull(event.getTicketPdf());
   CorrelationData correlation=invocation.getArgument(3);correlation.getFuture().complete(new CorrelationData.Confirm(true,null));return null;
  }).when(rabbit).convertAndSend(anyString(),anyString(),any(NotificationEvent.class),any(CorrelationData.class));
  jobs.deliverNotifications();assertNotNull(pending.getDeliveredAt());verifyNoInteractions(pdf,relay);verify(repo).save(pending);
 }
 @Test void brokerFailureUsesDurableHttpFallbackAndFailureOfBothKeepsOutboxPending(){
  var repo=mock(BookingNotificationRepository.class);var rabbit=mock(RabbitTemplate.class);var relay=mock(NotificationRelay.class);var clock=Clock.systemUTC();
  var event=new BookingNotification();event.setPnr("1234567890");event.setEventId("1234567890:CANCELLED");event.setEventType("CANCELLED");
  when(repo.findTop50ByDeliveredAtIsNullAndNextAttemptAtLessThanEqual(any())).thenReturn(List.of(event));
  var jobs=new ReservationJobs(mock(BookingEngine.class),mock(TicketPdfService.class),repo,rabbit,clock,relay);
  ReflectionTestUtils.setField(jobs,"exchange","railway_exchange");ReflectionTestUtils.setField(jobs,"routingKey","notification.email");
  doThrow(new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException())).when(rabbit).convertAndSend(anyString(),anyString(),any(NotificationEvent.class),any(CorrelationData.class));
  jobs.deliverNotifications();assertNotNull(event.getDeliveredAt());verify(relay).deliver(any(NotificationEvent.class));
  event.setDeliveredAt(null);doThrow(new IllegalStateException("Unavailable")).when(relay).deliver(any());jobs.deliverNotifications();assertNull(event.getDeliveredAt());assertEquals(1,event.getAttempts());assertTrue(event.getNextAttemptAt().isAfter(clock.instant()));
 }
 @Test void publishesSnapshotWithTicketAndWaitsForBrokerAcknowledgement(){
  var repo=mock(BookingNotificationRepository.class);var pdf=mock(TicketPdfService.class);var rabbit=mock(RabbitTemplate.class);var clock=Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC);
  var jobs=new ReservationJobs(mock(BookingEngine.class),pdf,repo,rabbit,clock,mock(NotificationRelay.class));ReflectionTestUtils.setField(jobs,"exchange","railway_exchange");ReflectionTestUtils.setField(jobs,"routingKey","notification.email");
  var e=new BookingNotification();e.setPnr("1234567890");e.setEventId("1234567890:PROMOTED");e.setEventType("PROMOTED");e.setBookingJson("{\"pnr\":\"1234567890\",\"status\":\"CONFIRMED\"}");
  when(repo.findTop50ByDeliveredAtIsNullAndNextAttemptAtLessThanEqual(clock.instant())).thenReturn(List.of(e));when(pdf.render(any())).thenReturn("%PDF-fixture".getBytes());
  doAnswer(i->{NotificationEvent event=i.getArgument(2);assertEquals(e.getBookingJson(),event.getBookingJson());assertArrayEquals("%PDF-fixture".getBytes(),event.getTicketPdf());CorrelationData c=i.getArgument(3);c.getFuture().complete(new CorrelationData.Confirm(true,null));return null;}).when(rabbit).convertAndSend(eq("railway_exchange"),eq("notification.email"),any(NotificationEvent.class),any(CorrelationData.class));
  new tools.jackson.databind.json.JsonMapper().readValue(e.getBookingJson(),ReservationRes.class);
  jobs.deliverNotifications();assertEquals(clock.instant(),e.getDeliveredAt());verify(repo).save(e);
 }
 @Test void pdfFailureKeepsConfirmationPendingRatherThanSendingWithoutAttachment(){
  var repo=mock(BookingNotificationRepository.class);var pdf=mock(TicketPdfService.class);var rabbit=mock(RabbitTemplate.class);var clock=Clock.systemUTC();
  var e=new BookingNotification();e.setPnr("1234567890");e.setEventType("PAYMENT_RECEIVED");e.setBookingJson("{\"pnr\":\"1234567890\"}");when(repo.findTop50ByDeliveredAtIsNullAndNextAttemptAtLessThanEqual(any())).thenReturn(List.of(e));when(pdf.render(any())).thenThrow(new IllegalStateException("Font missing"));
  new ReservationJobs(mock(BookingEngine.class),pdf,repo,rabbit,clock,mock(NotificationRelay.class)).deliverNotifications();assertNull(e.getDeliveredAt());assertEquals(1,e.getAttempts());verifyNoInteractions(rabbit);
 }
}

