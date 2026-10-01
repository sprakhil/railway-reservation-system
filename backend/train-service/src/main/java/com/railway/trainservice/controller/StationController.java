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
    public ResponseEntity<com.railway.trainservice.dto.PageResult<StationDto>> getAllStations(
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="stationCode") String sort,@RequestParam(defaultValue="asc") String direction) {
        return ResponseEntity.ok(stationService.page(page,size,sort,direction));
    }

    // SecurityConfig blocks this unless user has ROLE_ADMIN
    @PostMapping
    public ResponseEntity<StationDto> addStation(@Valid @RequestBody StationDto dto) {
        return new ResponseEntity<>(stationService.addStation(dto), HttpStatus.CREATED);
    }

    @GetMapping("/{stationId}")
    public ResponseEntity<StationDto> getStation(@PathVariable Long stationId) {
        return ResponseEntity.ok(stationService.getStationById(stationId));
    }

    @GetMapping("/code/{stationCode}")
    public ResponseEntity<StationDto> getStationByCode(@PathVariable String stationCode) {
        return ResponseEntity.ok(stationService.getStationByCode(stationCode));
    }

    @PutMapping("/{stationId}")
    public ResponseEntity<StationDto> updateStation(@PathVariable Long stationId, @Valid @RequestBody StationDto dto) {
        return ResponseEntity.ok(stationService.updateStation(stationId, dto));
    }
}
