package com.railway.notificationservice.service;
import com.railway.notificationservice.dto.NotificationEvent;
import com.railway.notificationservice.entity.NotificationLog;
import com.railway.notificationservice.repository.NotificationLogRepository;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {
 @Test void operatorCancellationEmailHasFullRefundReasonAndNoBankCreditClaim(){
  var e=event("TRAIN_CANCELLED");e.setMessage("Train cancelled. Full refund without deduction. Review separately booked onward journeys.");
  e.setBookingJson(e.getBookingJson().replace("\"status\":\"CONFIRMED\"","\"status\":\"CANCELLED\",\"runDate\":\"2030-01-01\",\"operatorCancellationReason\":\"Flooding <bridge>\"").replace("\"cancellationFee\":240","\"cancellationFee\":0").replace("\"refundAmount\":960","\"refundAmount\":1200"));
  var result=BookingEmailTemplate.render(e,"http://localhost:4200","Provider processing may take longer.",Instant.now());
  assertTrue(result.subject().contains("train has been cancelled"));assertTrue(result.plain().contains("Cancellation fee: INR 0.00"));assertTrue(result.plain().contains("Refund amount: INR 1200.00"));
  assertTrue(result.html().contains("Flooding &lt;bridge&gt;"));assertFalse(result.html().contains("Flooding <bridge>"));assertTrue(result.plain().contains("Original run date: 2030-01-01"));assertTrue(result.plain().contains("onward journeys"));
 }
 JavaMailSender sender; NotificationLogRepository logs; EmailService emails;
 @BeforeEach void setup(){sender=mock(JavaMailSender.class);logs=mock(NotificationLogRepository.class);emails=new EmailService(sender,logs);when(sender.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));}
 private NotificationEvent event(String type){return NotificationEvent.builder().eventId("1234567890:"+type).eventType(type).pnr("1234567890").username("Asha").userEmail("asha@example.test").trainNumber("56565").dateOfJourney("2030-01-02").bookingJson("""
 {"trainName":"Konkan Express","status":"CONFIRMED","fromStation":"PVL","toStation":"GOA","seatClass":"SL","departureAt":"2030-01-02T09:40:00Z","holdExpiresAt":"2030-01-01T00:02:00Z","totalFare":1200,"cancellationFee":240,"refundAmount":960,"refundStatus":"PENDING","passengers":[{"name":"Asha <script>alert(1)</script>","seatNumber":"SL-C1-12"}]}
 """).build();}
 private List<Part> parts(Part part)throws Exception {List<Part> result=new ArrayList<>();result.add(part);if(part.getContent() instanceof Multipart m)for(int i=0;i<m.getCount();i++)result.addAll(parts(m.getBodyPart(i)));return result;}
 @Test void multipartEmailIncludesPlainHtmlEmbeddedLogoAndPdf()throws Exception {
  var e=event("PAYMENT_RECEIVED");e.setTicketPdf("%PDF-test".getBytes());assertTrue(emails.processTicketConfirmation(e));
  var cap=ArgumentCaptor.forClass(MimeMessage.class);verify(sender).send(cap.capture());var msg=cap.getValue();msg.saveChanges();
  assertTrue(msg.getSubject().contains("confirmed"));assertEquals("asha@example.test",msg.getAllRecipients()[0].toString());
  var all=parts(msg);assertTrue(all.stream().anyMatch(p->{try{return p.isMimeType("text/plain");}catch(Exception ex){return false;}}));
  var html=all.stream().filter(p->{try{return p.isMimeType("text/html");}catch(Exception ex){return false;}}).findFirst().orElseThrow().getContent().toString();
  assertTrue(html.contains("cid:railwize-logo"));assertTrue(html.contains("SL-C1-12"));assertTrue(html.contains("15:10 IST"));assertTrue(html.contains("&lt;script&gt;"));assertFalse(html.contains("<script>"));
  assertTrue(all.stream().anyMatch(p->{try{return "Railwize-1234567890.pdf".equals(p.getFileName());}catch(Exception ex){return false;}}));
  var log=ArgumentCaptor.forClass(NotificationLog.class);verify(logs).save(log.capture());assertEquals(NotificationLog.NotificationStatus.SUCCESS,log.getValue().getStatus());
 }
 @Test void smtpFailureIsLoggedForRetry(){doThrow(new org.springframework.mail.MailSendException("SMTP unavailable")).when(sender).send(any(MimeMessage.class));assertFalse(emails.processTicketConfirmation(event("CANCELLED")));var cap=ArgumentCaptor.forClass(NotificationLog.class);verify(logs).save(cap.capture());assertEquals(NotificationLog.NotificationStatus.FAILED,cap.getValue().getStatus());}
 @Test void bookingAndPromotionEmailsUseServerBerthDescriptions(){
  for(String type:List.of("PAYMENT_RECEIVED","PROMOTED","CANCELLED")){
   var e=event(type);e.setBookingJson(e.getBookingJson().replace("\"seatNumber\":\"SL-C1-12\"","\"seatNumber\":\"SL-C1-12\",\"seatDetails\":{\"description\":\"Coach S1 · Berth 12 · Lower berth\"}"));
   var rendered=BookingEmailTemplate.render(e,"http://localhost:4200","",Instant.now());
   assertTrue(rendered.plain().contains("Coach S1 · Berth 12 · Lower berth"));
   assertTrue(rendered.html().contains("Coach S1 · Berth 12 · Lower berth"));assertFalse(rendered.html().contains("SL-C1-12"));
  }
 }
 @Test void cancellationAndRefundUseAccurateAmountsAndTestModeLanguage(){
  var c=BookingEmailTemplate.render(event("CANCELLED"),"http://localhost:4200","Test reconciliation normally checks every 30 seconds.",Instant.now());
  assertTrue(c.plain().contains("Cancellation fee: INR 240.00"));assertTrue(c.plain().contains("Refund amount: INR 960.00"));assertTrue(c.plain().contains("All passengers"));assertTrue(c.plain().contains("30 seconds"));
  var r=BookingEmailTemplate.render(event("REFUND_COMPLETED"),"http://localhost:4200","",Instant.now());assertTrue(r.plain().contains("No real money was charged or credited"));assertFalse(r.html().contains("successfully credited"));
 }
 @Test void retryLabelExpiresWithHoldAndWaitlistNeverClaimsConfirmation(){
  var e=event("PAYMENT_FAILED");var before=BookingEmailTemplate.render(e,"http://localhost:4200","",Instant.parse("2030-01-01T00:00:00Z"));assertTrue(before.html().contains("retry payment"));
  var after=BookingEmailTemplate.render(e,"http://localhost:4200","",Instant.parse("2030-01-01T00:03:00Z"));assertFalse(after.html().contains("retry payment"));assertTrue(after.plain().contains("deadline has passed"));
  e=event("PAYMENT_RECEIVED");e.setBookingJson(e.getBookingJson().replace("CONFIRMED","WAITING"));var wl=BookingEmailTemplate.render(e,"http://localhost:4200","",Instant.now());assertTrue(wl.subject().contains("waitlist"));assertFalse(wl.subject().contains("confirmed"));
 }
 @Test void writesRepresentativeTemplatePreviews()throws Exception {
  Path dir=Path.of("target/email-preview");Files.createDirectories(dir);
  String logo=Base64.getEncoder().encodeToString(getClass().getResourceAsStream("/mail/railwize-logo.png").readAllBytes());
  for(String type:List.of("PAYMENT_RECEIVED","PROMOTED","PAYMENT_FAILED","PAYMENT_INCOMPLETE","CANCELLED","REFUND_COMPLETED")){
   var preview=event(type);preview.setBookingJson(preview.getBookingJson().replace("Asha <script>alert(1)</script>","Asha Sharma"));preview.setTicketPdf("%PDF-preview".getBytes());
   var c=BookingEmailTemplate.render(preview,"http://localhost:4200","Test refunds are reconciled approximately every 30 seconds. Provider delays may take longer.",Instant.parse("2030-01-01T00:00:00Z"));
   Files.writeString(dir.resolve(type.toLowerCase()+".html"),c.html().replace("cid:railwize-logo","data:image/png;base64,"+logo));
  }
 }
}
