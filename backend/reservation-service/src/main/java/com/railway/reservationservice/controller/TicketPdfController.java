package com.railway.reservationservice.controller;
import com.railway.reservationservice.service.TicketPdfService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import org.springframework.security.core.Authentication; import org.springframework.http.*;
@RestController @RequiredArgsConstructor
public class TicketPdfController {
    private final TicketPdfService tickets;
    @GetMapping(value="/api/reservations/{pnr}/ticket.pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable String pnr,Authentication auth) {
        if(!pnr.matches("[0-9]{10}")) throw new IllegalArgumentException("Invalid PNR");
        boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=demo-booking-"+pnr+".pdf")
            .body(tickets.download(pnr,auth.getName(),admin));
    }
}
