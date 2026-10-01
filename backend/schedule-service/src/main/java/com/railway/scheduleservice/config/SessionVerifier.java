package com.railway.scheduleservice.config;
import io.jsonwebtoken.Claims; import org.springframework.stereotype.Component; import org.springframework.beans.factory.annotation.Value; import org.springframework.web.client.RestClient; import org.springframework.http.client.SimpleClientHttpRequestFactory; import java.util.Map;
@Component public class SessionVerifier {
 @Value("${railway.session.check-enabled:true}") private boolean enabled=true;
 @Value("${railway.auth.url:http://localhost:8081}") private String authUrl;
 @Value("${railway.internal.token:}") private String token;
 public boolean active(Claims c) {
  if(!enabled) return true;
  String sid=c.get("sid",String.class); if(sid==null || token==null || token.length()<32) return false;
  var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(2000); factory.setReadTimeout(2000);
  Map<?,?> result=RestClient.builder().requestFactory(factory).baseUrl(authUrl).build().post().uri("/internal/auth/session-check").header("X-Service-Token",token)
   .body(Map.of("sid",sid,"username",c.getSubject(),"role",c.get("role",String.class))).retrieve().body(Map.class);
  return result!=null && Boolean.TRUE.equals(result.get("active"));
 }
}
