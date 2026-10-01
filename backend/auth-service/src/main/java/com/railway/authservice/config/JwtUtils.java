package com.railway.authservice.config;

import com.railway.authservice.entity.AuthUser;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expirationMs}") // 15 mins = 900000
    private int jwtExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims getClaimsFromToken(String token) { return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload(); }

    public String generateAccessToken(AuthUser user) { return generateAccessToken(user, java.util.UUID.randomUUID().toString()); }

    public String generateAccessToken(AuthUser user,String sessionId) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("sid",sessionId)
                .claim("email", user.getEmail()) // <-- THE MAGIC HAPPENS HERE
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }
}
