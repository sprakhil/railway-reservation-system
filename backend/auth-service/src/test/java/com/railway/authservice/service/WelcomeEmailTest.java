package com.railway.authservice.service;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WelcomeEmailTest {
 private String html(Part p)throws Exception{if(p.isMimeType("text/html"))return p.getContent().toString();if(p.getContent() instanceof Multipart m)for(int i=0;i<m.getCount();i++){String h=html(m.getBodyPart(i));if(h!=null)return h;}return null;}
 @Test void welcomeUsesInlineBrandingAndVerifiedAccountWording()throws Exception{
  var sender=mock(JavaMailSender.class);when(sender.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));
  new EmailService(sender).sendRegistrationEmail("traveller@example.test","Asha <guest>");
  var cap=ArgumentCaptor.forClass(MimeMessage.class);verify(sender).send(cap.capture());var message=cap.getValue();message.saveChanges();
  assertTrue(message.getSubject().contains("active"));String body=html(message);assertNotNull(body);assertTrue(body.contains("cid:railwize-logo"));assertTrue(body.contains("email is verified"));assertTrue(body.contains("Asha &lt;guest&gt;"));assertTrue(body.contains("/auth/login"));
  var dir=Path.of("target/email-preview");Files.createDirectories(dir);var logo=getClass().getResourceAsStream("/mail/railwize-logo.png").readAllBytes();Files.writeString(dir.resolve("welcome.html"),body.replace("cid:railwize-logo","data:image/png;base64,"+Base64.getEncoder().encodeToString(logo)));
 }
 @Test void unavailableMailDoesNotUndoTheVerifiedAccount(){var sender=mock(JavaMailSender.class);when(sender.createMimeMessage()).thenThrow(new org.springframework.mail.MailSendException("Unavailable"));assertDoesNotThrow(()->new EmailService(sender).sendRegistrationEmail("a@example.test","Asha"));}
}
