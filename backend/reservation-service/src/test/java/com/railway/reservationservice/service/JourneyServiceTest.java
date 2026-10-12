package com.railway.reservationservice.service;

import com.railway.reservationservice.client.*;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.exception.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JourneyServiceTest {
    private final TrainCatalogClient trains = mock(TrainCatalogClient.class);
    private final ScheduleCatalogClient schedules = mock(ScheduleCatalogClient.class);
    private final LocalDate date = LocalDate.of(2030, 1, 2);
    private final JourneyService service = new JourneyService(trains, schedules,
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),mock(com.railway.reservationservice.repository.TrainRunCancellationRepository.class));
    private CatalogTrain train;
    private List<CatalogStop> route;

    @BeforeEach void setup() { resetFixture(); }

    private void resetFixture() {
        train = new CatalogTrain(); train.setTrainNumber("12345"); train.setTrainName("Demo Express");
        train.setSeatClasses(Set.of("SL")); train.setOperatingDays(EnumSet.allOf(DayOfWeek.class));
        var config = new CatalogTrain.ClassConfiguration();
        config.setSeatClass("SL"); config.setCoachCount(2); config.setSeatsPerCoach(72);
        config.setBaseFare(new BigDecimal("20.00")); config.setRatePerKm(new BigDecimal("0.50"));
        config.setMinimumFare(new BigDecimal("40.00")); train.setClassConfigurations(List.of(config));
        route = new ArrayList<>(List.of(stop("AAA", 1, 0, 10), stop("BBB", 2, 100, 11), stop("CCC", 3, 200, 12)));
        when(trains.getTrain("12345")).thenReturn(train);
        when(schedules.getSchedule("12345")).thenReturn(route);
    }

    private CatalogStop stop(String code, int sequence, int km, int hour) {
        CatalogStop stop = new CatalogStop(); stop.setStationCode(code); stop.setStopSequence(sequence);
        stop.setDistanceKm(km); stop.setDayNumber(1);
        // Harmless legacy endpoint arrival/departure fields remain supported.
        stop.setArrivalTime(LocalTime.of(hour, 0)); stop.setDepartureTime(LocalTime.of(hour, 5));
        return stop;
    }

    private JourneyQuote quote() { return service.quote("12345", "AAA", "CCC", date, "sl", 2); }
    private void conflict() {
        BookingException failure = assertThrows(BookingException.class, this::quote);
        assertEquals(HttpStatus.CONFLICT, failure.getStatus());
    }
    private CatalogTrain.ClassConfiguration config() { return train.getClassConfigurations().getFirst(); }

    @Test void discoveryIncludesAnAcOnlyTrainAndFetchesEachTimetableOnce() {
        var ac=new CatalogTrain.ClassConfiguration();ac.setSeatClass("CLASS_3A");ac.setCoachCount(1);ac.setSeatsPerCoach(72);
        ac.setBaseFare(new BigDecimal("100"));ac.setRatePerKm(new BigDecimal("1.50"));ac.setMinimumFare(new BigDecimal("300"));
        train.setSeatClasses(Set.of("CLASS_3A"));train.setClassConfigurations(List.of(ac));
        when(trains.getTrains()).thenReturn(List.of(train));
        var results=service.searchAllClasses(" aaa ","CCC",date);
        assertEquals(1,results.size());assertEquals("CLASS_3A",results.getFirst().getSeatClass());
        assertEquals(1,results.getFirst().getPassengerCount());assertEquals(new BigDecimal("400.00"),results.getFirst().getFarePerPassenger());
        verify(schedules,times(1)).getSchedule("12345");
    }

    @Test void discoveryHasEveryClassAndHonoursDirectionAndDateValidation() {
        var second=new CatalogTrain.ClassConfiguration();second.setSeatClass("CLASS_2A");second.setCoachCount(1);second.setSeatsPerCoach(48);
        second.setBaseFare(new BigDecimal("150"));second.setRatePerKm(new BigDecimal("2"));second.setMinimumFare(new BigDecimal("300"));
        train.setSeatClasses(Set.of("SL","CLASS_2A"));train.setClassConfigurations(List.of(config(),second));
        when(trains.getTrains()).thenReturn(List.of(train));
        var results=service.searchAllClasses("AAA","CCC",date);
        assertEquals(List.of("SL","CLASS_2A"),results.stream().map(JourneyQuote::getSeatClass).toList());
        verify(schedules,times(1)).getSchedule("12345");
        assertTrue(service.searchAllClasses("CCC","AAA",date).isEmpty());
        assertThrows(BookingException.class,()->service.searchAllClasses("AAA","AAA",date));
        assertThrows(BookingException.class,()->service.searchAllClasses("AAA","CCC",date.minusYears(1)));
        assertThrows(BookingException.class,()->service.searchAllClasses("AAA","CCC",date.plusMonths(3)));
    }

    @Test void calculatesExactFareAndKeepsCanonicalInventoryClass() {
        JourneyQuote quote = quote();
        assertEquals(new BigDecimal("120.00"), quote.getFarePerPassenger());
        assertEquals(new BigDecimal("240.00"), quote.getTotalFare());
        assertEquals("SL", quote.getSeatClass()); assertEquals(144, quote.capacity());
        assertEquals(date, quote.getRunDate());
        route.getFirst().setArrivalTime(null); route.getLast().setDepartureTime(null);
        assertEquals(new BigDecimal("240.00"), quote().getTotalFare());
    }

    @Test void explicitMidnightStopResolvesBoardingDateAgainstOriginalRunDay() {
        route.getFirst().setArrivalTime(null); route.getFirst().setDepartureTime(LocalTime.of(22, 0));
        route.get(1).setArrivalTime(LocalTime.of(23, 55)); route.get(1).setDepartureTime(LocalTime.of(0, 5));
        route.get(1).setDepartureDayNumber(2);
        route.getLast().setDayNumber(2); route.getLast().setArrivalTime(LocalTime.of(3, 0));
        route.getLast().setDepartureTime(null);
        JourneyQuote quote = service.quote("12345", "BBB", "CCC", date, "SL", 1);
        assertEquals(date.minusDays(1), quote.getRunDate());
        assertEquals(new BigDecimal("70.00"), quote.getTotalFare());
        route.get(1).setDepartureDayNumber(null);
        conflict();
    }

    @Test void validatesInteriorStopsEvenWhenEndpointsFormAValidJourney() {
        route.get(1).setArrivalTime(LocalTime.of(9, 0)); conflict();
        resetFixture(); route.get(1).setDepartureTime(LocalTime.of(12, 1)); conflict();
        resetFixture(); route.get(1).setDepartureTime(LocalTime.of(10, 59)); conflict();
        resetFixture(); route.get(1).setStationCode("AAA"); conflict();
        resetFixture(); route.get(1).setDistanceKm(300); conflict();
    }

    @Test void rejectsMissingNonconsecutiveAndRepeatedSequences() {
        for (Integer sequence : Arrays.asList(null, 0, 1, 3)) {
            resetFixture(); route.get(1).setStopSequence(sequence); conflict();
        }
        resetFixture(); Collections.swap(route, 0, 1); conflict();
    }

    @Test void rejectsOutOfRangeAndInconsistentDayOffsets() {
        for (Integer day : Arrays.asList(null, 0, 31)) {
            resetFixture(); route.get(1).setDayNumber(day); conflict();
        }
        resetFixture(); route.getFirst().setDayNumber(2); conflict();
        resetFixture(); route.get(1).setDepartureDayNumber(0); conflict();
        resetFixture(); route.get(1).setDepartureDayNumber(31); conflict();
        resetFixture(); route.getFirst().setDepartureDayNumber(2); conflict();
    }

    @Test void rejectsMissingNegativeEqualAndExcessiveDistances() {
        for (Integer distance : Arrays.asList(null, -1, 0, 20001)) {
            resetFixture(); route.get(1).setDistanceKm(distance); conflict();
        }
        resetFixture(); route.getFirst().setDistanceKm(1); conflict();
        resetFixture(); route.getLast().setDistanceKm(100); conflict();
    }

    @Test void rejectsNullMalformedOrIncompleteTimetables() {
        when(schedules.getSchedule("12345")).thenReturn(null); conflict();
        when(schedules.getSchedule("12345")).thenReturn(List.of()); conflict();
        when(schedules.getSchedule("12345")).thenReturn(List.of(route.getFirst())); conflict();
        resetFixture(); route.set(1, null); conflict();
        resetFixture(); route.get(1).setStationCode(null); conflict();
        resetFixture(); route.get(1).setStationCode("wrong code"); conflict();
        resetFixture(); route.get(1).setArrivalTime(null); conflict();
        resetFixture(); route.get(1).setDepartureTime(null); conflict();
        resetFixture(); route.getFirst().setDepartureTime(null); conflict();
        resetFixture(); route.getLast().setArrivalTime(null); conflict();
        resetFixture(); route.get(1).setArrivalTime(LocalTime.of(11, 0, 0, 1)); conflict();
    }

    @Test void rejectsCapacityOverflowAndUnavailableInventory() {
        for (int coaches : new int[] {0, -1, 31, Integer.MAX_VALUE}) {
            resetFixture(); config().setCoachCount(coaches); conflict();
        }
        for (int seats : new int[] {0, -1, 101, Integer.MAX_VALUE}) {
            resetFixture(); config().setSeatsPerCoach(seats); conflict();
        }
    }

    @Test void rejectsInvalidMonetaryParametersBeforeAnyFareArithmetic() {
        for (BigDecimal value : Arrays.asList(null, new BigDecimal("-0.01"), new BigDecimal("0.001"), new BigDecimal("10000000.00"))) {
            List<Consumer<BigDecimal>> setters = List.of(v -> config().setBaseFare(v), v -> config().setRatePerKm(v), v -> config().setMinimumFare(v));
            for (Consumer<BigDecimal> setter : setters) {
                resetFixture(); setter.accept(value); conflict();
            }
        }
    }

    @Test void rejectsDuplicatePartialUnknownAndNullClassConfiguration() {
        train.setClassConfigurations(List.of(config(), config())); conflict();
        resetFixture(); train.setSeatClasses(Set.of("SL", "CLASS_3A")); conflict();
        resetFixture(); config().setSeatClass("UNKNOWN"); conflict();
        resetFixture(); config().setSeatClass(null); conflict();
        resetFixture(); train.setClassConfigurations(Arrays.asList(config(), null)); conflict();
        resetFixture(); train.setClassConfigurations(null); conflict();
        resetFixture(); train.setClassConfigurations(List.of()); conflict();
        resetFixture(); train.setSeatClasses(Set.of()); conflict();
    }

    @Test void legacyTrainNeedsExplicitOperatingDaysAndInventory() {
        train.setOperatingDays(null); conflict();
        resetFixture(); train.setOperatingDays(Set.of()); conflict();
        resetFixture(); train.setOperatingDays(new HashSet<>(Arrays.asList(DayOfWeek.MONDAY, null))); conflict();
        when(trains.getTrain("12345")).thenReturn(null); conflict();
    }

    @Test void zeroFareIsNotBookableAndMinimumFareIsApplied() {
        config().setBaseFare(BigDecimal.ZERO); config().setRatePerKm(BigDecimal.ZERO); config().setMinimumFare(BigDecimal.ZERO);
        conflict();
        config().setMinimumFare(new BigDecimal("40.00"));
        assertEquals(new BigDecimal("80.00"), quote().getTotalFare());
    }

    @Test void searchExcludesMalformedTrainInsteadOfFailingWithNullPointerOrReturningBookableSeats() {
        CatalogTrain malformed = new CatalogTrain(); malformed.setTrainNumber("99999");
        when(trains.getTrains()).thenReturn(Arrays.asList(malformed, null, train));
        List<JourneyQuote> results = service.search("AAA", "CCC", date, "SL", 1);
        assertEquals(1, results.size()); assertEquals("12345", results.getFirst().getTrainNumber());
        verify(schedules, never()).getSchedule("99999");
        when(trains.getTrains()).thenReturn(null);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(BookingException.class,
                () -> service.search("AAA", "CCC", date, "SL", 1)).getStatus());
    }
}
