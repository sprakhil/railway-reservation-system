package com.railway.apigateway.config;
import org.springframework.stereotype.Component; import org.springframework.core.annotation.Order; import org.springframework.web.server.*; import reactor.core.publisher.Mono; import java.util.UUID;
@Component @Order(-100)
public class RequestIdFilter implements WebFilter {
 public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){String id=exchange.getRequest().getHeaders().getFirst("X-Request-ID");if(id==null||!id.matches("[A-Za-z0-9-]{8,80}"))id=UUID.randomUUID().toString();String requestId=id;exchange.getAttributes().put("requestId",id);exchange.getResponse().getHeaders().set("X-Request-ID",id);return chain.filter(exchange.mutate().request(r->r.headers(h->h.set("X-Request-ID",requestId))).build());}
}
