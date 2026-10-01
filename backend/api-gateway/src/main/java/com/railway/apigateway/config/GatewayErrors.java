package com.railway.apigateway.config;
import org.springframework.stereotype.Component; import org.springframework.core.annotation.Order; import org.springframework.web.server.*; import org.springframework.http.*;
import reactor.core.publisher.Mono; import java.time.Instant; import java.util.*; import tools.jackson.databind.json.JsonMapper;
@Component @Order(-2)
public class GatewayErrors implements WebExceptionHandler {
 private final JsonMapper json=JsonMapper.builder().build();
 public Mono<Void> handle(ServerWebExchange exchange,Throwable ex) {
  if(exchange.getResponse().isCommitted())return Mono.error(ex);
  int status=ex instanceof ResponseStatusException e?e.getStatusCode().value():503;
  var known=HttpStatus.resolve(status);String code=known==null?"REQUEST_FAILED":known.name();
  String requestId=exchange.getAttribute("requestId");if(requestId==null)requestId=UUID.randomUUID().toString();
  var body=Map.of("code",code,"status",status,"message",status>=500?"A required service is temporarily unavailable":known==null?"Request failed":known.getReasonPhrase(),"fieldErrors",Map.of(),"timestamp",Instant.now().toString(),"requestId",requestId);
  exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);exchange.getResponse().getHeaders().set("X-Request-ID",requestId);
  return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(json.writeValueAsBytes(body))));
 }
}
