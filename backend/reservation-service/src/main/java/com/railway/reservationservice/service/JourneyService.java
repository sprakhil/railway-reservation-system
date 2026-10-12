package com.railway.reservationservice.service;

import com.railway.reservationservice.client.*;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.exception.BookingException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class JourneyService {
    private static final ZoneId RAILWAY_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Set<String> SEAT_CLASSES = Set.of("CLASS_1A", "CLASS_2A", "CLASS_3A", "CLASS_3E", "EC", "SL", "CLASS_2S", "UR");
    private static final BigDecimal MAX_FARE_PARAMETER = new BigDecimal("9999999.99");
    private final TrainCatalogClient trains;
    private final ScheduleCatalogClient schedules;
    private final Clock clock;
    private final com.railway.reservationservice.repository.TrainRunCancellationRepository cancelledRuns;

    public JourneyQuote quote(String trainNumber,String from,String to,LocalDate boardingDate,String seatClass,int count) {
        from = ReservationReq.normalizeStation(from);
        to = ReservationReq.normalizeStation(to);
        validateSearch(from,to,boardingDate,seatClass,count);
        try { return quote(trains.getTrain(trainNumber), from,to,boardingDate,seatClass,count); }
        catch (FeignException.NotFound e) { throw new BookingException(HttpStatus.NOT_FOUND,"Train or timetable not found"); }
        catch (FeignException e) { throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE,"Train catalogue is temporarily unavailable"); }
    }

    public List<JourneyQuote> search(String from,String to,LocalDate date,String seatClass,int count) {
        from = ReservationReq.normalizeStation(from);
        to = ReservationReq.normalizeStation(to);
        validateSearch(from,to,date,seatClass,count);
        List<JourneyQuote> result = new ArrayList<>();
        try {
            List<CatalogTrain> catalogue = trains.getTrains();
            if (catalogue == null) throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE,"Train catalogue is temporarily unavailable");
            for (CatalogTrain train : catalogue) {
                try { result.add(quote(train,from,to,date,seatClass,count)); }
                catch (BookingException e) { if(e.getStatus()!=HttpStatus.CONFLICT) throw e; }
                catch (FeignException.NotFound e) { /* A train without a published timetable is not bookable. */ }
            }
        } catch (FeignException e) { throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE,"Train catalogue is temporarily unavailable"); }
        return result.stream().sorted(Comparator.comparing(JourneyQuote::getDepartureAt)).toList();
    }

    public List<JourneyQuote> searchAllClasses(String from,String to,LocalDate date) {
        from=ReservationReq.normalizeStation(from);to=ReservationReq.normalizeStation(to);
        validateSearch(from,to,date,"SL",1);
        List<JourneyQuote> results=new ArrayList<>();
        try {
            List<CatalogTrain> catalogue=trains.getTrains();
            if(catalogue==null) throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE,"Train catalogue is temporarily unavailable");
            for(var train:catalogue) {
                try {
                    validateTrainConfiguration(train);
                    // Fetch each timetable once, regardless of the number of classes offered.
                    var route=schedules.getSchedule(train.getTrainNumber());
                    for(String clazz:train.getSeatClasses().stream().sorted().toList()) {
                        try { results.add(quoteRoute(train,route,from,to,date,clazz,1)); }
                        catch(BookingException ex) { if(ex.getStatus()!=HttpStatus.CONFLICT)throw ex; }
                    }
                } catch(BookingException ex) { if(ex.getStatus()!=HttpStatus.CONFLICT)throw ex; }
                catch(FeignException.NotFound missingTimetable) { /* Unpublished trains cannot be booked. */ }
            }
        } catch(FeignException ex) {
            throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE,"Train catalogue is temporarily unavailable");
        }
        return results.stream().sorted(Comparator.comparing(JourneyQuote::getDepartureAt).thenComparing(JourneyQuote::getTrainNumber).thenComparing(JourneyQuote::getFarePerPassenger)).toList();
    }

    void validateSearch(String from,String to,LocalDate date,String seatClass,int count) {
        if(from==null || from.isBlank() || to==null || to.isBlank() || from.equalsIgnoreCase(to))
            throw BookingException.badRequest("Choose different boarding and destination stations");
        LocalDate today = LocalDate.now(clock.withZone(RAILWAY_ZONE));
        if(date==null || date.isBefore(today))
            throw BookingException.badRequest("Boarding date must be today or later");
        if(date.isAfter(today.plusMonths(2)))
            throw BookingException.badRequest("Boarding date must be within two calendar months from today (India time)");
        if(count<1 || count>6) throw BookingException.badRequest("A booking must contain 1 to 6 passengers");
        if(!from.matches("[A-Z0-9]{1,10}") || !to.matches("[A-Z0-9]{1,10}")) throw BookingException.badRequest("Use valid station codes");
        if(seatClass==null || !SEAT_CLASSES.contains(seatClass.toUpperCase(Locale.ROOT))) throw BookingException.badRequest("Choose a supported seat class");
    }

    private JourneyQuote quote(CatalogTrain train,String from,String to,LocalDate date,String seatClass,int count) {
        validateTrainConfiguration(train);
        return quoteRoute(train,schedules.getSchedule(train.getTrainNumber()),from,to,date,seatClass,count);
    }

    // Reuse a request-local timetable snapshot when evaluating multiple connections.
    JourneyQuote quoteRoute(CatalogTrain train,List<CatalogStop> stops,String from,String to,LocalDate date,String seatClass,int count) {
        validateTrainConfiguration(train);
        var config = train.getClassConfigurations().stream()
            .filter(c -> c.getSeatClass().equalsIgnoreCase(seatClass)).findFirst()
            .orElseThrow(() -> BookingException.conflict("This class has no configured inventory and fares"));
        validateRoute(stops);
        CatalogStop origin = stops.stream().filter(s->s.getStationCode().equalsIgnoreCase(from)).findFirst()
            .orElseThrow(()->BookingException.conflict("Boarding station is not served by this train"));
        CatalogStop destination = stops.stream().filter(s->s.getStationCode().equalsIgnoreCase(to)).findFirst()
            .orElseThrow(()->BookingException.conflict("Destination is not served by this train"));
        if(origin.getStopSequence()>=destination.getStopSequence()) throw BookingException.conflict("Destination must follow boarding station");
        if(origin.getDayNumber()==null || destination.getDayNumber()==null || origin.getDepartureTime()==null || destination.getArrivalTime()==null || origin.getDistanceKm()==null || destination.getDistanceKm()==null)
            throw BookingException.conflict("The published timetable is incomplete");
        int departureDay = origin.getDepartureDayNumber()==null ? origin.getDayNumber() : origin.getDepartureDayNumber();
        LocalDate runDate = date.minusDays(departureDay-1L);
        if(cancelledRuns.existsById(InventoryInitializer.runKey(train.getTrainNumber(),runDate)))
            throw BookingException.conflict("This train run has been cancelled by the operator. Choose another service or date.");
        if(train.getOperatingDays()==null || !train.getOperatingDays().contains(runDate.getDayOfWeek()))
            throw BookingException.conflict("Train does not operate on this run date");
        Instant departure = date.atTime(origin.getDepartureTime()).atZone(RAILWAY_ZONE).toInstant();
        Instant arrival = runDate.plusDays(destination.getDayNumber()-1L).atTime(destination.getArrivalTime()).atZone(RAILWAY_ZONE).toInstant();
        if(!departure.isAfter(clock.instant())) throw BookingException.conflict("Boarding time has passed");
        int distance = destination.getDistanceKm()-origin.getDistanceKm();
        if(distance<=0 || !arrival.isAfter(departure)) throw BookingException.conflict("Timetable distance or arrival time is invalid");
        BigDecimal fare = config.getBaseFare().add(config.getRatePerKm().multiply(BigDecimal.valueOf(distance)))
            .max(config.getMinimumFare()).setScale(2,RoundingMode.HALF_UP);
        if(fare.signum()<=0) throw BookingException.conflict("Fare must be positive");
        BigDecimal total = fare.multiply(BigDecimal.valueOf(count)).setScale(2,RoundingMode.UNNECESSARY);
        if(total.compareTo(new BigDecimal("9999999999.99"))>0) throw BookingException.conflict("Fare exceeds the supported amount");
        return JourneyQuote.builder().trainNumber(train.getTrainNumber()).trainName(train.getTrainName())
            .fromStation(origin.getStationCode()).toStation(destination.getStationCode()).seatClass(config.getSeatClass())
            .boardingDate(date).runDate(runDate).departureAt(departure).arrivalAt(arrival)
            .fromSequence(origin.getStopSequence()).toSequence(destination.getStopSequence()).distanceKm(distance)
            .passengerCount(count).coachCount(config.getCoachCount()).seatsPerCoach(config.getSeatsPerCoach())
            .baseFare(config.getBaseFare()).ratePerKm(config.getRatePerKm()).minimumFare(config.getMinimumFare())
            .farePerPassenger(fare).totalFare(total).build();
    }

    void validateTrainConfiguration(CatalogTrain train) {
        if (train == null || train.getTrainNumber() == null || !train.getTrainNumber().matches("[A-Z0-9]{1,10}")
                || train.getOperatingDays() == null || train.getOperatingDays().isEmpty()
                || train.getOperatingDays().stream().anyMatch(Objects::isNull)
                || train.getSeatClasses() == null || train.getSeatClasses().isEmpty()
                || train.getSeatClasses().stream().anyMatch(c -> c == null || !SEAT_CLASSES.contains(c))
                || train.getClassConfigurations() == null || train.getClassConfigurations().isEmpty()
                || train.getClassConfigurations().size() > SEAT_CLASSES.size())
            throw BookingException.conflict("Train operating days, inventory and fares must be configured before booking");
        Set<String> configured = new HashSet<>();
        for (CatalogTrain.ClassConfiguration config : train.getClassConfigurations()) {
            if (config == null || config.getSeatClass() == null || !train.getSeatClasses().contains(config.getSeatClass())
                    || !configured.add(config.getSeatClass()) || config.getCoachCount() < 1 || config.getCoachCount() > 30
                    || config.getSeatsPerCoach() < 1 || config.getSeatsPerCoach() > 100
                    || !validFareParameter(config.getBaseFare()) || !validFareParameter(config.getRatePerKm())
                    || !validFareParameter(config.getMinimumFare()))
                throw BookingException.conflict("Train inventory or fare configuration is invalid");
        }
        if (!configured.equals(train.getSeatClasses()))
            throw BookingException.conflict("Every declared seat class must have configured inventory and fares");
    }

    private boolean validFareParameter(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.compareTo(MAX_FARE_PARAMETER) <= 0
                && value.stripTrailingZeros().scale() <= 2;
    }

    /** Legacy timetables were not validated when published. Never allocate seats against a malformed route. */
    void validateRoute(List<CatalogStop> stops) {
        if (stops == null || stops.size() < 2 || stops.size() > 200)
            throw BookingException.conflict("The published timetable must contain 2-200 valid stops");
        Set<String> stations = new HashSet<>();
        long previousDeparture = -1;
        int previousDistance = -1;
        for (int i = 0; i < stops.size(); i++) {
            CatalogStop stop = stops.get(i);
            if (stop == null || stop.getStationCode() == null || !stop.getStationCode().matches("[A-Z0-9]{1,10}")
                    || !stations.add(stop.getStationCode()) || stop.getStopSequence() == null || stop.getStopSequence() != i + 1
                    || stop.getDayNumber() == null || stop.getDayNumber() < 1 || stop.getDayNumber() > 30
                    || stop.getDistanceKm() == null || stop.getDistanceKm() <= previousDistance || stop.getDistanceKm() > 20000)
                throw BookingException.conflict("The published timetable has invalid stations, stop order, days or distances");
            int departureDay = stop.getDepartureDayNumber() == null ? stop.getDayNumber() : stop.getDepartureDayNumber();
            if (departureDay < stop.getDayNumber() || departureDay > 30
                    || (i == 0 && (stop.getDistanceKm() != 0 || stop.getDayNumber() != 1 || departureDay != 1))
                    || (i > 0 && stop.getArrivalTime() == null) || (i < stops.size() - 1 && stop.getDepartureTime() == null)
                    || (stop.getArrivalTime() != null && stop.getArrivalTime().getNano() != 0)
                    || (stop.getDepartureTime() != null && stop.getDepartureTime().getNano() != 0))
                throw BookingException.conflict("The published timetable has incomplete or invalid arrival/departure days and times");
            long arrival = stop.getArrivalTime() == null ? -1 : absoluteSeconds(stop.getDayNumber(), stop.getArrivalTime());
            long departure = stop.getDepartureTime() == null ? -1 : absoluteSeconds(departureDay, stop.getDepartureTime());
            if ((i > 0 && arrival <= previousDeparture)
                    || (arrival >= 0 && departure >= 0 && departure < arrival))
                throw BookingException.conflict("The published timetable contains an impossible stop chronology");
            previousDeparture = departure;
            previousDistance = stop.getDistanceKm();
        }
    }

    private long absoluteSeconds(int day, LocalTime time) {
        return (day - 1L) * 86400 + time.toSecondOfDay();
    }
}
