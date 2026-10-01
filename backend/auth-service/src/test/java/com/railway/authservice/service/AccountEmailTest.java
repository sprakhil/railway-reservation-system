package com.railway.authservice.service;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountEmailTest {
 private JavaMailSender mail; private OtpEmailService emails;
 @BeforeEach void setup(){mail=mock(JavaMailSender.class);when(mail.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));emails=new OtpEmailService(mail);}
 private List<Part> parts(Part p)throws Exception{var found=new ArrayList<Part>();found.add(p);if(p.getContent() instanceof Multipart m)for(int i=0;i<m.getCount();i++)found.addAll(parts(m.getBodyPart(i)));return found;}
 private String text(List<Part> parts,String type)throws Exception{for(var p:parts)if(p.isMimeType(type))return p.getContent().toString();throw new AssertionError("Missing "+type);}
 @Test void registrationHasLeadingZeroCodeExpiryHtmlPlainAndLogo()throws Exception{
  emails.sendOtpEmail("asha@example.test","012345");var cap=ArgumentCaptor.forClass(MimeMessage.class);verify(mail).send(cap.capture());var message=cap.getValue();message.saveChanges();var all=parts(message);
  assertTrue(message.getSubject().startsWith("Railwize"));assertFalse(message.getSubject().contains("012345"));assertEquals("asha@example.test",message.getAllRecipients()[0].toString());
  String html=text(all,"text/html"),plain=text(all,"text/plain");assertTrue(html.contains("012345"));assertTrue(html.contains("5 minutes"));assertTrue(html.contains("/auth/verify"));assertFalse(html.contains("otp=012345"));assertTrue(plain.contains("EMAIL VERIFICATION CODE: 012345"));assertTrue(html.contains("cid:railwize-logo"));
  assertTrue(all.stream().anyMatch(part->{try{return part.isMimeType("image/png");}catch(Exception e){return false;}}));
 }
 @Test void resetMailExplainsPurposeAndDoesNotClaimPasswordAlreadyChanged()throws Exception{
  emails.sendPasswordResetOtp("asha@example.test","246810");var cap=ArgumentCaptor.forClass(MimeMessage.class);verify(mail).send(cap.capture());cap.getValue().saveChanges();var all=parts(cap.getValue());var html=text(all,"text/html");var plain=text(all,"text/plain");assertTrue(html.contains("/auth/reset"));assertTrue(html.contains("Your password has not been changed"));assertTrue(plain.contains("PASSWORD RESET CODE: 246810"));assertTrue(plain.contains("5 minutes"));assertTrue(plain.contains("Never share"));
 }
 @Test void smtpFailuresRemainFailuresForBothOtpPurposes(){doThrow(new org.springframework.mail.MailSendException("Unavailable")).when(mail).send(any(MimeMessage.class));assertThrows(RuntimeException.class,()->emails.sendOtpEmail("a@example.test","012345"));assertThrows(RuntimeException.class,()->emails.sendPasswordResetOtp("a@example.test","012345"));}
 @Test void templatesEscapeDynamicValuesAndInvitationKeepsSingleUseLink(){
  var content=AccountEmailTemplate.otp("<script>",false,"http://localhost:4200");assertTrue(content.html().contains("&lt;script&gt;"));assertFalse(content.html().contains("<script>"));
  var invite=AccountEmailTemplate.invitation("A&B@example.test","http://localhost:4200/admin/accept-invitation#token=fixture-token",Instant.parse("2030-01-02T10:00:00Z"));
  assertTrue(invite.html().contains("A&amp;B@example.test"));assertTrue(invite.html().contains("#token=fixture-token"));assertTrue(invite.plain().contains("single-use"));assertTrue(invite.plain().contains("24 hours"));assertTrue(invite.html().contains("02 Jan 2030, 15:30 IST"));assertTrue(invite.plain().contains("verified email account"));
 }
 @Test void writesBrandedAccountPreviews()throws Exception{
  var dir=Path.of("target/email-preview");Files.createDirectories(dir);var logo=Base64.getEncoder().encodeToString(getClass().getResourceAsStream("/mail/railwize-logo.png").readAllBytes());
  var content=Map.of("registration-otp",AccountEmailTemplate.otp("012345",false,"http://localhost:4200"),"password-reset",AccountEmailTemplate.otp("246810",true,"http://localhost:4200"),"admin-invitation",AccountEmailTemplate.invitation("asha@example.test","http://localhost:4200/admin/accept-invitation#token=preview-not-valid",Instant.parse("2030-01-02T10:00:00Z")));
  for(var entry:content.entrySet())Files.writeString(dir.resolve(entry.getKey()+".html"),entry.getValue().html().replace("cid:railwize-logo","data:image/png;base64,"+logo));
 }
}
