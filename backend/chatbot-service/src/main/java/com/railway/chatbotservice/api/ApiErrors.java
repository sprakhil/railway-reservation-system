package com.railway.chatbotservice.api;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.*;
@RestControllerAdvice
public class ApiErrors {
    public static Map<String,Object> body(int status,String message,String requestId){return Map.of("status",status,"message",message,"code",status==401?"UNAUTHORIZED":status==404?"NOT_FOUND":status==400?"VALIDATION_ERROR":"CHAT_ERROR","requestId",requestId,"fieldErrors",Map.of(),"timestamp",Instant.now().toString());}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handle(Exception e,ServerWebExchange exchange){
        int status=e instanceof ChatException ce?ce.status:e instanceof org.springframework.web.bind.support.WebExchangeBindException||e instanceof ServerWebInputException?400:500;
        String message=e instanceof ChatException?e.getMessage():status==400?"Provide a non-empty message of at most 2000 characters":"Chat is temporarily unavailable. Please try again.";
        return ResponseEntity.status(status).body(body(status,message,exchange.getRequest().getId()));
    }
    public static Mono<Void> write(ServerWebExchange exchange,int status,String message){
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] json=new tools.jackson.databind.json.JsonMapper().writeValueAsBytes(body(status,message,exchange.getRequest().getId()));
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(json)));
    }
}
