package com.railway.reservationservice.config;
import io.jsonwebtoken.*; import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException; import java.util.List;
import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter; import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
 private final JwtUtils jwtUtils; private final SessionVerifier sessions;
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
  String header=request.getHeader("Authorization");
  if(header!=null) {
   try {
    if(!header.startsWith("Bearer ")) throw new IllegalArgumentException();
    Claims c=jwtUtils.getClaimsFromToken(header.substring(7)); String user=c.getSubject(),role=c.get("role",String.class);
    if(user==null || user.isBlank() || !("PASSENGER".equals(role)||"ADMIN".equals(role)) || !sessions.active(c)) throw new IllegalArgumentException();
    request.setAttribute("loggedInUserEmail",c.get("email",String.class)); request.setAttribute("sessionId",c.get("sid",String.class));
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
   } catch(JwtException|IllegalArgumentException ex) { SecurityContextHolder.clearContext(); response.sendError(401,"Invalid, expired or revoked access token"); return; }
     catch(org.springframework.web.client.RestClientException ex) { SecurityContextHolder.clearContext(); response.sendError(503,"Session service is temporarily unavailable"); return; }
  }
  chain.doFilter(request,response);
 }
 @Override protected boolean shouldNotFilter(HttpServletRequest r){String p=r.getRequestURI();return p.contains("/v3/api-docs")||p.contains("/swagger-ui")||p.startsWith("/internal/");}
}
