package com.railway.notificationservice.controller;

import com.railway.notificationservice.dto.NotificationEvent;
import com.railway.notificationservice.service.NotificationInbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Durable handoff when the message broker is unavailable. Never exposed by the gateway. */
@RestController
@io.swagger.v3.oas.annotations.Hidden
public class InternalNotificationController {
    private final NotificationInbox inbox;
    private final String token;
    public InternalNotificationController(NotificationInbox inbox,@Value("${railway.internal.token:}") String token){this.inbox=inbox;this.token=token;}
    @PostMapping("/internal/notifications/events") @ResponseStatus(HttpStatus.ACCEPTED)
    public void accept(@RequestHeader(value="X-Service-Token",required=false) String supplied,@RequestBody NotificationEvent event){
        if(token.length()<32||supplied==null||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid service credentials");
        // Return success only after the transactional inbox has committed. Redelivery uses the same event ID.
        inbox.accept(event);
    }
}
