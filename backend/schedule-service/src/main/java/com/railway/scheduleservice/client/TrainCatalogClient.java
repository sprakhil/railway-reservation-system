package com.railway.scheduleservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "train-service")
public interface TrainCatalogClient {
    @GetMapping("/api/trains/{trainNumber}")
    CatalogTrain getTrain(@PathVariable("trainNumber") String trainNumber);
    @GetMapping("/api/stations/code/{stationCode}")
    CatalogStation getStation(@PathVariable("stationCode") String stationCode);
    record CatalogTrain(String trainNumber, Long sourceStationId, Long destinationStationId) {}
    record CatalogStation(Long stationId, String stationCode) {}
}
