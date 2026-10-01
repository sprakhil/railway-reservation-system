package com.railway.reservationservice.service;
import com.railway.reservationservice.dto.NotificationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Component
public class NotificationRelay {
    private final RestClient client;
    private final String token;
    public NotificationRelay(@Value("${railway.notification.url:http://localhost:8088}") String url,@Value("${railway.internal.token:}") String token){
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(2000);factory.setReadTimeout(5000);
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();this.token=token;
    }
    public void deliver(NotificationEvent event){
        if(token.length()<32)throw new IllegalStateException("Internal notification credentials are not configured");
        client.post().uri("/internal/notifications/events").header("X-Service-Token",token).body(event).retrieve().toBodilessEntity();
    }
}
