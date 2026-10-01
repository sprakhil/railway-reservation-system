package com.railway.chatbotservice.config;

import com.railway.chatbotservice.api.ApiErrors;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.*;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.*;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Configuration @EnableWebFluxSecurity
public class SecurityConfig {
    public record Identity(String username,String token) implements java.security.Principal {public String getName(){return username;} @Override public String toString(){return username;}}
    @Bean SecurityWebFilterChain security(ServerHttpSecurity http,@Value("${jwt.secret}") String secret,
        @Value("${railway.auth.url:http://localhost:8081}") String authUrl,@Value("${railway.internal.token}") String internalToken) {
        var key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        var authClient=WebClient.builder().baseUrl(authUrl).build();
        org.springframework.web.server.WebFilter filter=(exchange,chain)->{
            String path=exchange.getRequest().getPath().value();
            if(path.startsWith("/actuator/")||path.contains("/v3/api-docs")||path.startsWith("/swagger-ui"))return chain.filter(exchange);
            String header=exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if(header==null||!header.startsWith("Bearer "))return ApiErrors.write(exchange,401,"Authentication required");
            Claims claims;
            try{claims=Jwts.parser().verifyWith(key).build().parseSignedClaims(header.substring(7)).getPayload();
                if(claims.getSubject()==null||claims.getSubject().isBlank()||claims.get("sid",String.class)==null||!Set.of("ADMIN","PASSENGER").contains(claims.get("role",String.class)))throw new IllegalArgumentException();
            }catch(Exception e){return ApiErrors.write(exchange,401,"Invalid or expired access token");}
            var identity=new Identity(claims.getSubject(),header.substring(7));
            var authentication=new UsernamePasswordAuthenticationToken(identity,null,List.of(new SimpleGrantedAuthority("ROLE_"+claims.get("role",String.class))));
            return authClient.post().uri("/internal/auth/session-check").header("X-Service-Token",internalToken)
                .bodyValue(Map.of("sid",claims.get("sid",String.class),"username",identity.username(),"role",claims.get("role",String.class)))
                .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(3)).map(r->Boolean.TRUE.equals(r.get("active"))?200:401).defaultIfEmpty(401)
                .onErrorReturn(503)
                .flatMap(status->status==200?chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)):ApiErrors.write(exchange,status,status==503?"Session verification is temporarily unavailable":"Your session has expired. Please sign in again."));
        };
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable).formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange(a->a.pathMatchers("/actuator/health","/api/chat/v3/api-docs/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll().anyExchange().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((x,f)->ApiErrors.write(x,401,"Authentication required")).accessDeniedHandler((x,f)->ApiErrors.write(x,403,"Access denied")))
            .addFilterAt(filter,SecurityWebFiltersOrder.AUTHENTICATION).build();
    }
}
