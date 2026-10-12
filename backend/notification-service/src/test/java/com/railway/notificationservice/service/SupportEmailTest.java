package com.railway.notificationservice.service;
import com.railway.notificationservice.dto.NotificationEvent;
import com.railway.notificationservice.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.internet.MimeMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class SupportEmailTest {
    @Autowired NotificationInbox inbox;@Autowired NotificationDeliveryRepository deliveries;@MockitoBean JavaMailSender sender;
    final String id="SUP-12345678-1234-1234-1234-123456789012";
    NotificationEvent event(){return NotificationEvent.builder().eventId("support-email-once").supportTicketId(id).eventType("SUPPORT_REPLY").userEmail("alice@example.test").username("alice").message("Support replied to <script>alert(1)</script>.").build();}
    @Test void supportWithoutBookingIsDurableBrandedAndDeduplicated() {
        when(sender.createMimeMessage()).thenAnswer(i->new MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
        var event=event();inbox.accept(event);inbox.accept(event);inbox.deliver(event.getEventId());inbox.deliver(event.getEventId());
        verify(sender,times(1)).send(any(MimeMessage.class));assertNotNull(deliveries.findById(event.getEventId()).orElseThrow().getSentAt());
        var content=SupportEmailTemplate.render(event,"http://localhost:4200/");assertTrue(content.html().contains("cid:railwize-logo"));
        assertTrue(content.html().contains("/support/"+id));assertFalse(content.html().contains("<script>"));assertFalse(content.html().contains("PNR: null"));
        event.setEventType("SUPPORT_NEW");assertTrue(SupportEmailTemplate.render(event,"http://localhost:4200").html().contains("/admin/support/"+id));
    }
    @Test void invalidSupportReferenceIsRejected(){var e=event();e.setSupportTicketId("../../admin");assertThrows(IllegalArgumentException.class,()->inbox.accept(e));}
}
