package com.railway.reservationservice.client;
import com.railway.reservationservice.dto.CatalogStop;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name = "schedule-service")
public interface ScheduleCatalogClient {
    @GetMapping("/api/schedules/{trainNumber}") List<CatalogStop> getSchedule(@PathVariable("trainNumber") String trainNumber);
}
