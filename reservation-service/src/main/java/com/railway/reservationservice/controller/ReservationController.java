package com.railway.reservationservice.controller;

import com.railway.reservationservice.dto.CancellationResponse;
import com.railway.reservationservice.dto.ReservationReq;
import com.railway.reservationservice.dto.ReservationRes;
import com.railway.reservationservice.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    // Secure: Username extracted from JWT
    @PostMapping("/book")
    public ResponseEntity<ReservationRes> bookTicket(
            @Valid @RequestBody ReservationReq request,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String username = authentication.getName();
        String email = (String) httpRequest.getAttribute("loggedInUserEmail");
        return new ResponseEntity<>(reservationService.bookTicket(username, email, request), HttpStatus.CREATED);
    }

    @GetMapping("/my-bookings")
    public ResponseEntity<List<ReservationRes>> getMyBookings(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(reservationService.getMyBookings(username));
    }

    @GetMapping("/pnr/{pnr}")
    public ResponseEntity<ReservationRes> getByPnr(@PathVariable String pnr) {
        return ResponseEntity.ok(reservationService.getReservationByPnr(pnr));
    }

    @DeleteMapping("/{pnr}")
    @Operation(summary = "Cancel a ticket", description = "Cancels a booking, calculates deductions, and initiates the refund process.")
    public ResponseEntity<CancellationResponse> cancelTicket(@PathVariable String pnr) {
        CancellationResponse response = reservationService.cancelReservation(pnr);
        return ResponseEntity.ok(response);
    }
}