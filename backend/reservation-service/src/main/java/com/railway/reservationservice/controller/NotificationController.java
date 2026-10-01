package com.railway.reservationservice.controller;

import com.railway.reservationservice.service.NotificationCenter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/reservations/notifications") @RequiredArgsConstructor
@Tag(name="Notifications",description="Private booking updates for the signed-in account, including ADMIN accounts' own bookings.")
public class NotificationController {
    private final NotificationCenter center;
    @GetMapping @Operation(summary="List my notifications and unread count",description="Newest first. Zero-based page; size 1-100. Optional unreadOnly filter. No other account's inbox can be accessed.")
    public NotificationCenter.Inbox list(Authentication auth,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="false") boolean unreadOnly) {
        return center.list(auth.getName(),page,size,unreadOnly);
    }
    @PutMapping("/{id}/read") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(summary="Mark my notification as read",description="Idempotent. Missing or other-account IDs return 404.")
    public void read(Authentication auth,@PathVariable String id){center.read(auth.getName(),id);}
    @PutMapping("/read-all") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(summary="Mark all my current notifications as read")
    public void readAll(Authentication auth){center.readAll(auth.getName());}
}
