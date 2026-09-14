package com.railway.scheduleservice.controller;

import com.railway.scheduleservice.dto.ScheduleDto;
import com.railway.scheduleservice.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    // Public Endpoint
    @GetMapping("/{trainNumber}")
    public ResponseEntity<List<ScheduleDto>> getSchedule(@PathVariable String trainNumber) {
        return ResponseEntity.ok(scheduleService.getScheduleByTrainNumber(trainNumber));
    }

    // Admin Endpoint: Bulk save/update entire route
    @PostMapping("/{trainNumber}")
    public ResponseEntity<List<ScheduleDto>> addSchedule(
            @PathVariable String trainNumber,
            @RequestBody @Valid List<ScheduleDto> scheduleDtos) {
        return new ResponseEntity<>(scheduleService.saveTrainSchedule(trainNumber, scheduleDtos), HttpStatus.CREATED);
    }
}