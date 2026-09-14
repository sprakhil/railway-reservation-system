package com.railway.reservationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

// Resolves the IP of the PNR service dynamically from Eureka
@FeignClient(name = "pnr-service")
public interface PnrServiceClient {

    @GetMapping("/api/pnr/generate")
    String generatePnr();
}