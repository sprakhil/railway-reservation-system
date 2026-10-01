package com.railway.reservationservice.client;
import com.railway.reservationservice.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="train-service",contextId="plannerStations")
public interface StationCatalogClient {
    @GetMapping("/api/stations") PageResult<CatalogStation> page(@RequestParam("page") int page,@RequestParam("size") int size);
    @GetMapping("/api/stations/code/{code}") CatalogStation station(@PathVariable("code") String code);
}
