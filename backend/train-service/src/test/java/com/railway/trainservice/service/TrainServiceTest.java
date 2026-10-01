package com.railway.trainservice.service;

import com.railway.trainservice.dto.*;
import com.railway.trainservice.entity.*;
import com.railway.trainservice.exception.*;
import com.railway.trainservice.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainServiceTest {
    @Mock TrainRepository trainRepository;
    @Mock StationRepository stationRepository;
    @InjectMocks TrainService service;

    private TrainClassConfigDto config() {
        return TrainClassConfigDto.builder().seatClass(SeatClass.SL).coachCount(2).seatsPerCoach(72)
                .baseFare(new BigDecimal("20.00")).ratePerKm(new BigDecimal("0.50"))
                .minimumFare(new BigDecimal("40.00")).build();
    }
    private TrainDto input() {
        return TrainDto.builder().trainNumber("12045").trainName("Demo Express").category(TrainCategory.EXPRESS)
                .sourceStationId(1L).destinationStationId(2L).seatClasses(Set.of(SeatClass.SL))
                .operatingDays(Set.of(DayOfWeek.MONDAY)).classConfigurations(List.of(config())).build();
    }
    private Train existing() {
        return Train.builder().trainNumber("12045").trainName("Demo Express")
                .sourceStation(Station.builder().stationId(1L).build())
                .destinationStation(Station.builder().stationId(2L).build()).seatClasses(Set.of(SeatClass.SL))
                .operatingDays(Set.of(DayOfWeek.MONDAY))
                .classConfigurations(List.of(TrainClassConfiguration.builder().seatClass(SeatClass.SL)
                        .coachCount(2).seatsPerCoach(72).baseFare(new BigDecimal("20.00"))
                        .ratePerKm(new BigDecimal("0.50")).minimumFare(new BigDecimal("40.00")).build())).build();
    }
    @Test void savesConfiguredInventoryAndExactFare() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(existing().getSourceStation()));
        when(stationRepository.findById(2L)).thenReturn(Optional.of(existing().getDestinationStation()));
        when(trainRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TrainDto result = service.addTrain(input());
        assertEquals(72, result.getClassConfigurations().getFirst().getSeatsPerCoach());
        assertEquals(new BigDecimal("0.50"), result.getClassConfigurations().getFirst().getRatePerKm());
        assertEquals(Set.of(DayOfWeek.MONDAY), result.getOperatingDays());
    }
    @Test void legacyTrainHasNoInventedInventoryOrOperatingDays() {
        Train legacy = existing(); legacy.setClassConfigurations(null); legacy.setOperatingDays(null);
        when(trainRepository.findByTrainNumber("12045")).thenReturn(Optional.of(legacy));
        TrainDto dto = service.getTrainByNumber("12045");
        assertTrue(dto.getClassConfigurations().isEmpty()); assertTrue(dto.getOperatingDays().isEmpty());
    }
    @Test void rejectsDuplicateClasses() {
        TrainDto dto = input(); dto.setClassConfigurations(List.of(config(), config()));
        assertThrows(IllegalArgumentException.class, () -> service.addTrain(dto));
        verify(trainRepository, never()).save(any());
    }
    @Test void rejectsCapacityBeyondBound() {
        TrainDto dto = input(); dto.getClassConfigurations().getFirst().setCoachCount(31);
        assertThrows(IllegalArgumentException.class, () -> service.addTrain(dto));
    }
    @Test void rejectsFractionOfPaiseAndNegativeMoney() {
        TrainDto dto = input(); dto.getClassConfigurations().getFirst().setRatePerKm(new BigDecimal("0.001"));
        assertThrows(IllegalArgumentException.class, () -> service.addTrain(dto));
        dto.getClassConfigurations().getFirst().setRatePerKm(new BigDecimal("-0.01"));
        assertThrows(IllegalArgumentException.class, () -> service.addTrain(dto));
    }
    @Test void requiresEveryDeclaredClassAndOperatingDays() {
        TrainDto dto = input(); dto.setSeatClasses(Set.of(SeatClass.SL, SeatClass.CLASS_3A));
        assertThrows(IllegalArgumentException.class, () -> service.addTrain(dto));
        TrainDto missingDays = input(); missingDays.setOperatingDays(Set.of());
        assertThrows(IllegalArgumentException.class, () -> service.addTrain(missingDays));
    }
    @Test void rejectsSameEndpoints() {
        TrainDto dto = input(); dto.setDestinationStationId(1L);
        assertThrows(IllegalArgumentException.class, () -> service.addTrain(dto));
    }
    @Test void rejectsUnknownStation() {
        assertThrows(ResourceNotFoundException.class, () -> service.addTrain(input()));
        verify(trainRepository, never()).save(any());
    }
    @Test void rejectsDuplicateTrainNumber() {
        when(trainRepository.existsByTrainNumber("12045")).thenReturn(true);
        assertThrows(CatalogConflictException.class, () -> service.addTrain(input()));
    }
    @Test void missingTrainIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.getTrainByNumber("99999"));
    }
    @Test void publishedCapacityCannotBeReducedOrDaysChanged() {
        when(trainRepository.findForUpdate("12045")).thenReturn(Optional.of(existing()));
        TrainDto dto = input(); dto.getClassConfigurations().getFirst().setCoachCount(1);
        assertThrows(CatalogConflictException.class, () -> service.updateTrain("12045", dto));
        dto.getClassConfigurations().getFirst().setCoachCount(2); dto.setOperatingDays(Set.of(DayOfWeek.TUESDAY));
        assertThrows(CatalogConflictException.class, () -> service.updateTrain("12045", dto));
        verify(trainRepository, never()).save(any());
    }
    @Test void publishedFareCanChangeWithoutRenumberingSeats() {
        when(trainRepository.findForUpdate("12045")).thenReturn(Optional.of(existing()));
        when(trainRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TrainDto dto = input(); dto.getClassConfigurations().getFirst().setRatePerKm(new BigDecimal("0.60"));
        assertEquals(new BigDecimal("0.60"), service.updateTrain("12045", dto).getClassConfigurations().getFirst().getRatePerKm());
    }
    @Test void routeIdentifiersCannotChangeEvenBeforeInventoryConfiguration() {
        Train legacy = existing(); legacy.setClassConfigurations(List.of());
        when(trainRepository.findForUpdate("12045")).thenReturn(Optional.of(legacy));
        TrainDto dto = input(); dto.setDestinationStationId(3L);
        assertThrows(CatalogConflictException.class, () -> service.updateTrain("12045", dto));
    }
}
