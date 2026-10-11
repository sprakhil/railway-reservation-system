package com.railway.reservationservice.service;

import com.railway.reservationservice.client.*;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.exception.BookingException;
import com.railway.reservationservice.repository.TrainRunCancellationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import java.time.*;

@Service @RequiredArgsConstructor
public class RunCancellationService {
    private final TrainCatalogClient trains;
    private final ScheduleCatalogClient schedules;
    private final JourneyService journeys;
    private final BookingEngine engine;
    private final TrainRunCancellationRepository cancelledRuns;
    private final Clock clock;

    public RunCancellationView preview(String train,LocalDate date) {
        return engine.previewRun(train,date,departure(train,date));
    }
    public RunCancellationView cancel(String train,LocalDate date,String reason,String admin) {
        if(reason==null || reason.strip().length()<5 || reason.strip().length()>500 || reason.chars().anyMatch(Character::isISOControl))
            throw BookingException.badRequest("Provide a cancellation reason of 5-500 characters without control characters");
        return engine.cancelRun(train,date,departure(train,date),reason.strip(),admin);
    }
    public PageResult<com.railway.reservationservice.entity.TrainRunCancellation> history(int page,int size) {
        if(page<0 || size<1 || size>100) throw BookingException.badRequest("Use a nonnegative page and size 1-100");
        return PageResult.of(cancelledRuns.findAll(PageRequest.of(page,size,Sort.by(Sort.Order.desc("cancelledAt"),Sort.Order.asc("runKey")))));
    }
    private Instant departure(String train,LocalDate date) {
        if(train==null || !train.matches("[A-Z0-9]{1,10}") || date==null) throw BookingException.badRequest("Choose a valid train number and original run date");
        var existing=cancelledRuns.findById(InventoryInitializer.runKey(train,date));
        if(existing.isPresent()) return existing.get().getSourceDepartureAt(); // Safe repeated requests, including after departure.
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        if(date.isBefore(today) || date.isAfter(today.plusMonths(2))) throw BookingException.badRequest("Run date must be within the next two calendar months");
        try {
            var trainInfo=trains.getTrain(train); var route=schedules.getSchedule(train);
            journeys.validateTrainConfiguration(trainInfo); journeys.validateRoute(route);
            if(!trainInfo.getOperatingDays().contains(date.getDayOfWeek())) throw BookingException.conflict("Train does not operate on this original run date");
            Instant departure=date.atTime(route.getFirst().getDepartureTime()).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
            if(!departure.isAfter(clock.instant())) throw BookingException.conflict("This run has already departed from its first station");
            return departure;
        } catch(feign.FeignException.NotFound ex) {throw new BookingException(HttpStatus.NOT_FOUND,"Train or published timetable not found");}
        catch(feign.FeignException ex) {throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE,"Catalogue unavailable; no cancellation was applied");}
    }
}
