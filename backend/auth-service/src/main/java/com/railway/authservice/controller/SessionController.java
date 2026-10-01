package com.railway.authservice.controller;
import com.railway.authservice.service.SessionService; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.HttpStatus; import org.springframework.security.core.Authentication; import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest; import java.security.MessageDigest; import java.nio.charset.StandardCharsets; import java.util.Map;
@RestController @RequiredArgsConstructor
public class SessionController {
 private final SessionService sessions;
 @Value("${railway.internal.token:}") private String internalToken;
 public record Check(String sid,String username,String role){}
 @PostMapping("/internal/auth/session-check") public Map<String,Boolean> check(@RequestHeader(value="X-Service-Token",required=false) String supplied,@RequestBody Check req){
  if(internalToken==null || internalToken.length()<32 || supplied==null || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid service credentials");
  return Map.of("active",sessions.active(req.sid(),req.username(),req.role()));
 }
 @PostMapping("/api/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(Authentication auth,HttpServletRequest request){sessions.logout((String)request.getAttribute("sessionId"),auth.getName(),false); if(request.getSession(false)!=null) request.getSession(false).invalidate();}
 @PostMapping("/api/auth/logout-all") @ResponseStatus(HttpStatus.NO_CONTENT) public void all(Authentication auth,HttpServletRequest request){sessions.logout((String)request.getAttribute("sessionId"),auth.getName(),true); if(request.getSession(false)!=null) request.getSession(false).invalidate();}
}
