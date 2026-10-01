package com.railway.reservationservice.controller;

import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/reservations") @RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservations;
    @GetMapping("/search")
    public List<JourneyQuote> search(@RequestParam String fromStation,@RequestParam String toStation,
        @RequestParam LocalDate dateOfJourney,@RequestParam String seatClass,@RequestParam(defaultValue="1") int passengers) {
        return reservations.search(fromStation,toStation,dateOfJourney,seatClass,passengers);
    }
    @GetMapping("/quote")
    public JourneyQuote quote(@RequestParam String trainNumber,@RequestParam String fromStation,@RequestParam String toStation,
        @RequestParam LocalDate dateOfJourney,@RequestParam String seatClass,@RequestParam(defaultValue="1") int passengers) {
        return reservations.quote(trainNumber,fromStation,toStation,dateOfJourney,seatClass,passengers);
    }
    @PostMapping("/book")
    public ResponseEntity<ReservationRes> bookTicket(@Valid @RequestBody ReservationReq request,
        @RequestHeader("Idempotency-Key") String key,Authentication authentication,jakarta.servlet.http.HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservations.bookTicket(authentication.getName(),
            (String)httpRequest.getAttribute("loggedInUserEmail"),key,request));
    }
    @GetMapping("/my-bookings") public PageResult<ReservationRes> getMyBookings(Authentication auth,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="bookingTime") String sort,@RequestParam(defaultValue="desc") String direction,
        @RequestParam(required=false) String status,@RequestParam(required=false) LocalDate fromDate,@RequestParam(required=false) LocalDate toDate) {
        return reservations.history(auth.getName(),page,size,sort,direction,status,fromDate,toDate);
    }
    @GetMapping("/my-bookings/overview")
    @io.swagger.v3.oas.annotations.Operation(summary="My booking counts and next journey",description="Owner only, including ADMIN accounts. Counts booking records by status. Next journey excludes departed, cancelled, expired and unpaid bookings; a waitlisted result is not confirmed travel.")
    public BookingOverview overview(Authentication auth){return reservations.overview(auth.getName());}
    @GetMapping("/my-bookings/{pnr}")
    @io.swagger.v3.oas.annotations.Operation(summary="Read only my own booking",description="Strict owner-only lookup, including for ADMIN accounts. Used by the read-only chatbot.")
    public ReservationRes ownBooking(@PathVariable String pnr,Authentication auth){return reservations.getReservationByPnr(pnr,auth.getName(),false);}
    @GetMapping("/{pnr}/cancellation-preview") public CancellationResponse preview(@PathVariable String pnr,Authentication auth){return reservations.preview(pnr,auth.getName(),admin(auth));}
    @GetMapping("/pnr/{pnr}") public ReservationRes getByPnr(@PathVariable String pnr,Authentication auth) {
        return reservations.getReservationByPnr(pnr,auth.getName(),admin(auth));
    }
    @DeleteMapping("/{pnr}") public CancellationResponse cancelTicket(@PathVariable String pnr,Authentication auth) {
        return reservations.cancelReservation(pnr,auth.getName(),admin(auth));
    }
    private boolean admin(Authentication auth) { return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")); }
}
