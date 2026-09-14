package com.railway.trainservice.controller;

import com.railway.trainservice.dto.StationDto;
import com.railway.trainservice.service.StationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    @GetMapping
    public ResponseEntity<List<StationDto>> getAllStations() {
        return ResponseEntity.ok(stationService.getAllStations());
    }

    // SecurityConfig blocks this unless user has ROLE_ADMIN
    @PostMapping
    public ResponseEntity<StationDto> addStation(@Valid @RequestBody StationDto dto) {
        return new ResponseEntity<>(stationService.addStation(dto), HttpStatus.CREATED);
    }
}