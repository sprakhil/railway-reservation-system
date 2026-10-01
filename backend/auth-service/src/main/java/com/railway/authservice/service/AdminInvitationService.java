package com.railway.authservice.service;
import com.railway.authservice.entity.*; import com.railway.authservice.repository.*; import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.mail.javamail.MimeMessageHelper; import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value; import org.springframework.web.server.ResponseStatusException; import org.springframework.http.HttpStatus; import org.springframework.data.domain.*;
import java.security.*; import java.nio.charset.StandardCharsets; import java.time.*; import java.util.*;
@Service @RequiredArgsConstructor
public class AdminInvitationService {
 private final AdminInvitationRepository invitations; private final AuthUserRepository users; private final RefreshTokenRepository sessions; private final JavaMailSender mail;
 @Value("${app.admin-invitation-uri:http://localhost:4200/admin/accept-invitation}") private String acceptUri;
 public record InvitationView(Long id,String email,String invitedBy,String acceptedBy,Instant expiresAt,Instant acceptedAt,Instant revokedAt){}
 @Value("${app.mail.from:${spring.mail.username:}}") private String from="";
 private InvitationView view(AdminInvitation i){return new InvitationView(i.getId(),i.getEmail(),i.getInvitedBy(),i.getAcceptedBy(),i.getExpiresAt(),i.getAcceptedAt(),i.getRevokedAt());}
 private void admin(String username){AuthUser u=users.findByUsername(username).orElseThrow(()->error("Admin account not found")); if(u.getRole()!=AuthUser.Role.ADMIN || u.getStatus()!=AuthUser.Status.ACTIVE) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin permission required");}
 @Transactional public InvitationView invite(String actor,String email) {
  admin(actor); String address=email.trim().toLowerCase(Locale.ROOT);
  byte[] secret=new byte[32]; new SecureRandom().nextBytes(secret); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
  AdminInvitation i=new AdminInvitation(); i.setEmail(address); i.setInvitedBy(actor); i.setTokenHash(hash(token)); i.setCreatedAt(Instant.now()); i.setExpiresAt(i.getCreatedAt().plus(Duration.ofHours(24))); invitations.save(i);
  try {
   var content=AccountEmailTemplate.invitation(address,acceptUri+"#token="+token,i.getExpiresAt());
   var message=mail.createMimeMessage();var helper=new MimeMessageHelper(message,true,"UTF-8");
   if(!from.isBlank())helper.setFrom(from,"Railwize");
   helper.setTo(address);helper.setSubject(content.subject());helper.setText(content.plain(),content.html());
   RailwizeMail.logo(helper);mail.send(message);
  } catch(jakarta.mail.MessagingException | java.io.IOException ex) {
   throw new org.springframework.mail.MailSendException("Could not send administrator invitation",ex);
  } return view(i);
 }
 @Transactional public void accept(String username,String token) {
  AdminInvitation i=invitations.lockHash(hash(token)).orElseThrow(()->error("Invitation is invalid or expired"));
  if(i.getAcceptedAt()!=null || i.getRevokedAt()!=null || !i.getExpiresAt().isAfter(Instant.now())) throw error("Invitation is invalid or expired");
  AuthUser u=users.findByEmailForUpdate(i.getEmail()).orElseThrow(()->error("Sign in with the invited verified email account"));
  if(!u.getUsername().equals(username) || u.getStatus()!=AuthUser.Status.ACTIVE || !(u.isVerified()||u.isEmailVerified())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invitation belongs to another verified account");
  u.setRole(AuthUser.Role.ADMIN); i.setAcceptedAt(Instant.now()); i.setAcceptedBy(username); sessions.deleteByUser(u);
 }
 @Transactional public void revoke(String actor,Long id){admin(actor); AdminInvitation i=invitations.lockId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invitation not found")); if(i.getAcceptedAt()!=null) throw error("Accepted invitations cannot be revoked"); i.setRevokedAt(Instant.now());}
 @Transactional(readOnly=true) public Map<String,Object> list(String actor,int page,int size){admin(actor); if(page<0||size<1||size>100) throw new IllegalArgumentException("Invalid pagination"); var p=invitations.findAll(PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))); return Map.of("content",p.map(this::view).getContent(),"page",page,"size",size,"totalElements",p.getTotalElements(),"totalPages",p.getTotalPages());}
 private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
 private ResponseStatusException error(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
