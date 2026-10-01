package com.railway.reservationservice.controller;
import com.railway.reservationservice.service.AdminBookingService;
import com.railway.reservationservice.service.AdminBookingService.Filter;
import com.railway.reservationservice.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController @RequestMapping("/api/reservations/admin") @RequiredArgsConstructor
@Tag(name="Admin booking reports",description="ADMIN only. Booking-count and fare analytics use journey-date cohorts, not capture/refund transaction dates. All money is test-mode INR. Excludes duplicate/late payment captures not attached as the booking's verified payment.")
public class AdminBookingController {
    private final AdminBookingService service;
    @GetMapping("/dashboard") @Operation(summary="All-time booking metrics and top ten trains by verified paid bookings")
    public AdminBookingService.Dashboard dashboard(){return service.dashboard();}
    @GetMapping("/bookings") public PageResult<AdminBookingService.Row> bookings(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String username,@RequestParam(defaultValue="") String trainNumber,@RequestParam(defaultValue="") String status,@RequestParam(required=false) LocalDate fromDate,@RequestParam(required=false) LocalDate toDate,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="bookingTime") String sort,@RequestParam(defaultValue="desc") String direction){return service.list(new Filter(search,username,trainNumber,status,fromDate,toDate),page,size,sort,direction);}
    @GetMapping("/reports") @Operation(summary="Revenue and cancellation cohorts by journey day, month or train; maximum 366 days")
    public AdminBookingService.Report report(@RequestParam LocalDate fromDate,@RequestParam LocalDate toDate,@RequestParam(defaultValue="") String trainNumber,@RequestParam(defaultValue="day") String groupBy){return service.report(fromDate,toDate,trainNumber,groupBy);}
    @GetMapping(value="/bookings/export",produces="text/csv") public ResponseEntity<String> export(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String username,@RequestParam(defaultValue="") String trainNumber,@RequestParam(defaultValue="") String status,@RequestParam(required=false) LocalDate fromDate,@RequestParam(required=false) LocalDate toDate,@RequestParam(defaultValue="false") boolean cancellations){return csv(service.export(new Filter(search,username,trainNumber,status,fromDate,toDate),cancellations),cancellations?"cancellations":"bookings");}
    @GetMapping(value="/reports/export",produces="text/csv") public ResponseEntity<String> revenue(@RequestParam LocalDate fromDate,@RequestParam LocalDate toDate,@RequestParam(defaultValue="") String trainNumber,@RequestParam(defaultValue="day") String groupBy){return csv(service.reportCsv(fromDate,toDate,trainNumber,groupBy),"revenue-cancellations");}
    private ResponseEntity<String> csv(String body,String name){return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8")).header("Content-Disposition","attachment; filename=railwize-"+name+".csv").header("Cache-Control","no-store").body(body);}
}
