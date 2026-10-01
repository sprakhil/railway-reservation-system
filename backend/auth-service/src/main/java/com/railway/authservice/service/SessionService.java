package com.railway.authservice.service;
import com.railway.authservice.entity.*; import com.railway.authservice.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import lombok.RequiredArgsConstructor; import java.time.Instant;
@Service @RequiredArgsConstructor
public class SessionService {
 private final RefreshTokenRepository tokens;
 @Transactional(readOnly=true) public boolean active(String sid,String username,String role) {
  if(sid==null || username==null || role==null) return false;
  return tokens.findBySessionId(sid).filter(t->t.getExpiryDate().isAfter(Instant.now())).map(t->{AuthUser u=t.getUser(); return u.getStatus()==AuthUser.Status.ACTIVE && (u.isVerified()||u.isEmailVerified()) && u.getUsername().equals(username) && u.getRole().name().equals(role);}).orElse(false);
 }
 @Transactional public void logout(String sid,String username,boolean all) {
  tokens.findBySessionId(sid).filter(t->t.getUser().getUsername().equals(username)).ifPresent(t->{if(all) tokens.deleteByUser(t.getUser());else tokens.delete(t);});
 }
}
