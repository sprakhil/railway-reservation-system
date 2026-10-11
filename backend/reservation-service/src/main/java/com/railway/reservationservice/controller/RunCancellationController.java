package com.railway.reservationservice.controller;
import com.railway.reservationservice.service.RunCancellationService;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.entity.TrainRunCancellation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@RestController @RequiredArgsConstructor @RequestMapping("/api/reservations/admin/train-runs")
@io.swagger.v3.oas.annotations.tags.Tag(name="Train run cancellations",description="ADMIN only. Run date is departure from the first station, in India time. Whole-run cancellation before source departure; full test refunds and durable notifications. No train deletion or reinstatement.")
public class RunCancellationController {
    private final RunCancellationService service;
    public record CancelRequest(@NotBlank @Size(min=5,max=500) String reason) {}
    @GetMapping public PageResult<TrainRunCancellation> history(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.history(page,size);}
    @GetMapping("/{trainNumber}/{runDate}")
    public RunCancellationView preview(@PathVariable String trainNumber,@PathVariable LocalDate runDate){return service.preview(trainNumber,runDate);}
    @PostMapping("/{trainNumber}/{runDate}/cancel")
    public RunCancellationView cancel(@PathVariable String trainNumber,@PathVariable LocalDate runDate,@Valid @RequestBody CancelRequest request,Authentication auth){return service.cancel(trainNumber,runDate,request.reason(),auth.getName());}
}
