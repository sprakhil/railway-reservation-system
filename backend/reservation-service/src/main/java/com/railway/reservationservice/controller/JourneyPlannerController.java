package com.railway.reservationservice.controller;
import com.railway.reservationservice.dto.JourneyPlan;
import com.railway.reservationservice.service.JourneyPlanner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/reservations") @RequiredArgsConstructor
public class JourneyPlannerController {
    private final JourneyPlanner planner;
    @GetMapping("/alternatives")
    @io.swagger.v3.oas.annotations.Operation(summary="Find connecting trains and nearby-station alternatives",description="Public read-only planner. One same-station interchange; each leg requires a separate booking/payment. Distances are straight-line estimates, not road distances. Availability is rechecked during booking.",security={})
    public JourneyPlan.Results alternatives(@RequestParam String fromStation,@RequestParam String toStation,
        @RequestParam LocalDate dateOfJourney,@RequestParam String seatClass,@RequestParam(defaultValue="1") int passengers,
        @RequestParam(defaultValue="true") boolean nearby,@RequestParam(defaultValue="50") int radiusKm,
        @RequestParam(defaultValue="60") int minTransferMinutes,@RequestParam(defaultValue="360") int maxTransferMinutes) {
        return planner.search(fromStation,toStation,dateOfJourney,seatClass,passengers,nearby,radiusKm,minTransferMinutes,maxTransferMinutes);
    }
}
