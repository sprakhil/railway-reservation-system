package com.railway.authservice.config;
import com.railway.authservice.service.SessionService; import io.jsonwebtoken.Claims; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class SessionVerifier { private final SessionService sessions; public boolean active(Claims c){return sessions.active(c.get("sid",String.class),c.getSubject(),c.get("role",String.class));} }
