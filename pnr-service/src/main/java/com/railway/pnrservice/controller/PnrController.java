package com.railway.pnrservice.controller;

import com.railway.pnrservice.service.PnrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pnr")
@RequiredArgsConstructor
public class PnrController {

    private final PnrService pnrService;

    @GetMapping("/generate")
    public ResponseEntity<String> generatePnr() {
        // Returns the 10 digit string directly
        return ResponseEntity.ok(pnrService.generateUniquePnr());
    }
}