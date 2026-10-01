package com.railway.notificationservice.service;
import com.railway.notificationservice.repository.NotificationDeliveryRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="railway.internal.token=test-internal-token-at-least-32-characters")
class NotificationHttpDeliveryTest {
    @LocalServerPort int port;
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired NotificationInbox inbox;
    @MockitoBean JavaMailSender sender;
    final HttpClient http=HttpClient.newHttpClient();
    @BeforeEach void setup(){deliveries.deleteAll();when(sender.createMimeMessage()).thenAnswer(i->new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));}
    HttpResponse<String> send(String json,String token)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/internal/notifications/events")).header("Content-Type","application/json");if(token!=null)request.header("X-Service-Token",token);
        return http.send(request.POST(HttpRequest.BodyPublishers.ofString(json)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String body(String type){return new tools.jackson.databind.json.JsonMapper().writeValueAsString(Map.of("eventId","1234567890:"+type,"eventType",type,"pnr","1234567890","username","Traveller","userEmail","traveller@example.invalid","trainNumber","12345","dateOfJourney","2030-01-02","message","Your booking update","bookingJson","{\"pnr\":\"1234567890\",\"status\":\"CONFIRMED\",\"totalFare\":600}","ticketPdf",Base64.getEncoder().encodeToString("%PDF-fixture".getBytes())));}
    @Test void internalEndpointRequiresServiceSecret()throws Exception{
        assertEquals(403,send(body("PAYMENT_RECEIVED"),null).statusCode());assertEquals(403,send(body("PAYMENT_RECEIVED"),"wrong").statusCode());assertEquals(0,deliveries.count());verify(sender,never()).send(any(MimeMessage.class));
    }
    @Test void bookingCancellationAndRefundSurviveHttpHandoffAndReachMailSenderOnce()throws Exception{
        for(String type:List.of("PAYMENT_RECEIVED","CANCELLED","REFUND_COMPLETED")){
            String id="1234567890:"+type;
            var response=send(body(type),"test-internal-token-at-least-32-characters");assertEquals(202,response.statusCode(),response.body());
            assertEquals(202,send(body(type),"test-internal-token-at-least-32-characters").statusCode());
            inbox.deliver(id);inbox.deliver(id);assertNotNull(deliveries.findById(id).orElseThrow().getSentAt());
        }
        var mails=org.mockito.ArgumentCaptor.forClass(MimeMessage.class);verify(sender,times(3)).send(mails.capture());
        var first=mails.getAllValues().getFirst();assertTrue(first.getSubject().length()>5);
        var bytes=new java.io.ByteArrayOutputStream();first.writeTo(bytes);assertTrue(bytes.toString().contains("Railwize-1234567890.pdf"));
    }
}
