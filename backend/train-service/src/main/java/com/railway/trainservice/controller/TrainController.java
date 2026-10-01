package com.railway.trainservice.controller;

import com.railway.trainservice.dto.TrainDto;
import com.railway.trainservice.service.TrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/trains")
@RequiredArgsConstructor
public class TrainController {

    private final TrainService trainService;

    @GetMapping
    public ResponseEntity<com.railway.trainservice.dto.PageResult<TrainDto>> getAllTrains(
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="trainNumber") String sort,@RequestParam(defaultValue="asc") String direction) {
        return ResponseEntity.ok(trainService.page(page,size,sort,direction));
    }

    @GetMapping("/{trainNumber}")
    public ResponseEntity<TrainDto> getTrain(@PathVariable String trainNumber) {
        return ResponseEntity.ok(trainService.getTrainByNumber(trainNumber));
    }

    // SecurityConfig blocks this unless user has ROLE_ADMIN
    @PostMapping
    public ResponseEntity<TrainDto> addTrain(@Valid @RequestBody TrainDto dto) {
        return new ResponseEntity<>(trainService.addTrain(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{trainNumber}")
    public ResponseEntity<TrainDto> updateTrain(@PathVariable String trainNumber, @Valid @RequestBody TrainDto dto) {
        return ResponseEntity.ok(trainService.updateTrain(trainNumber, dto));
    }
}
