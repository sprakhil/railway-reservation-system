package com.railway.reservationservice.service;
import com.railway.reservationservice.client.*;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.exception.BookingException;
import org.junit.jupiter.api.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JourneyPlannerTest {
    final TrainCatalogClient trains=mock(TrainCatalogClient.class);
    final StationCatalogClient stations=mock(StationCatalogClient.class);
    final ScheduleCatalogClient schedules=mock(ScheduleCatalogClient.class);
    final BookingEngine engine=mock(BookingEngine.class);
    final Clock clock=Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC);
    final LocalDate date=LocalDate.of(2030,1,2);
    final JourneyService quotes=new JourneyService(trains,schedules,clock);
    final JourneyPlanner planner=new JourneyPlanner(trains,stations,schedules,quotes,engine,clock);
    List<CatalogTrain> catalogue;
    @BeforeEach void setup(){
        catalogue=new ArrayList<>();when(trains.getPage(0,100)).thenAnswer(i->new PageResult<>(catalogue,0,100,catalogue.size(),1));
        when(stations.station("AAA")).thenReturn(station("AAA",19,73));when(stations.station("CCC")).thenReturn(station("CCC",15,74));
        when(stations.page(0,100)).thenReturn(new PageResult<>(List.of(station("AAA",19,73),station("CCC",15,74)),0,100,2,1));
        doAnswer(i->{JourneyQuote q=i.getArgument(0);q.setSuggestedAllocation("CNF");q.setAvailableSeats(10);return null;}).when(engine).describeAvailability(any());
        route("T1",List.of(stop("AAA",1,1,"10:00",0),stop("XXX",2,1,"12:00",100)));
        route("T2",List.of(stop("XXX",1,1,"13:00",0),stop("CCC",2,1,"15:00",100)));
    }
    static CatalogStation station(String code,double lat,double lon){return new CatalogStation(code,code,lat,lon);}
    static CatalogTrain train(String number){
        var t=new CatalogTrain();t.setTrainNumber(number);t.setTrainName("Train "+number);t.setOperatingDays(EnumSet.allOf(DayOfWeek.class));t.setSeatClasses(Set.of("SL"));
        var c=new CatalogTrain.ClassConfiguration();c.setSeatClass("SL");c.setCoachCount(1);c.setSeatsPerCoach(10);c.setBaseFare(BigDecimal.TEN);c.setRatePerKm(BigDecimal.ONE);c.setMinimumFare(BigDecimal.TEN);t.setClassConfigurations(List.of(c));return t;
    }
    static CatalogStop stop(String code,int sequence,int day,String time,int distance){
        var s=new CatalogStop();s.setStationCode(code);s.setStopSequence(sequence);s.setDayNumber(day);s.setDepartureDayNumber(day);s.setArrivalTime(LocalTime.parse(time));s.setDepartureTime(LocalTime.parse(time));s.setDistanceKm(distance);return s;
    }
    void route(String number,List<CatalogStop> stops){catalogue.removeIf(t->t.getTrainNumber().equals(number));catalogue.add(train(number));when(schedules.getSchedule(number)).thenReturn(stops);}
    JourneyPlan.Results search(boolean nearby){return planner.search(" aaa ","ccc",date,"SL",2,nearby,50,60,360);}
    @Test void connectsAndQuotesBothLegsWithoutCreatingBookings(){
        var result=search(false);assertEquals(1,result.options().size());var p=result.options().getFirst();assertEquals("CONNECTION",p.kind());assertEquals(60,p.transferMinutes());assertEquals(300,p.durationMinutes());assertEquals(new BigDecimal("440.00"),p.totalFare());assertEquals("CNF",p.allocation());
        verify(schedules,times(1)).getSchedule("T1");verify(schedules,times(1)).getSchedule("T2");verify(engine,times(2)).describeAvailability(any());verifyNoMoreInteractions(engine);
    }
    @Test void enforcesMinimumAndMaximumBuffers(){
        route("T2",List.of(stop("XXX",1,1,"12:59",0),stop("CCC",2,1,"15:00",100)));assertTrue(search(false).options().isEmpty());
        route("T2",List.of(stop("XXX",1,1,"18:01",0),stop("CCC",2,1,"20:00",100)));assertTrue(search(false).options().isEmpty());
        route("T2",List.of(stop("XXX",1,1,"18:00",0),stop("CCC",2,1,"20:00",100)));assertEquals(360,search(false).options().getFirst().transferMinutes());
    }
    @Test void overnightInterchangeUsesSecondBoardingDateAndOriginalTrainRunDay(){
        route("T1",List.of(stop("AAA",1,1,"22:00",0),stop("XXX",2,1,"23:30",100)));
        route("T2",List.of(stop("BBB",1,1,"23:45",0),stop("XXX",2,2,"00:45",50),stop("CCC",3,2,"03:00",150)));
        catalogue.getLast().setOperatingDays(Set.of(date.getDayOfWeek()));
        var p=search(false).options().getFirst();assertEquals(75,p.transferMinutes());assertEquals(date.plusDays(1),p.legs().getLast().getBoardingDate());assertEquals(date,p.legs().getLast().getRunDate());
        catalogue.getLast().setOperatingDays(Set.of(date.plusDays(1).getDayOfWeek()));assertTrue(search(false).options().isEmpty());
    }
    @Test void nearbyAlternativesCanChangeBothEndpointsAndExcludeOutOfRadiusStations(){
        var nearby=List.of(station("AAN",19.1,73),station("CCN",15.1,74),station("FAR",25,80));
        when(stations.page(0,100)).thenReturn(new PageResult<>(nearby,0,100,3,1));
        route("T3",List.of(stop("AAN",1,1,"09:00",0),stop("CCN",2,1,"14:00",200)));
        route("T4",List.of(stop("FAR",1,1,"09:00",0),stop("CCC",2,1,"14:00",200)));
        var p=search(true).options().stream().filter(x->x.kind().equals("NEARBY_DIRECT")).findFirst().orElseThrow();
        assertEquals("AAN",p.legs().getFirst().getFromStation());assertEquals("CCN",p.legs().getFirst().getToStation());assertEquals(11.1,p.originDistanceKm(),.1);assertEquals(11.1,p.destinationDistanceKm(),.1);
        assertTrue(search(true).options().stream().noneMatch(x->x.legs().getFirst().getTrainNumber().equals("T4")));
        assertEquals(1,search(false).options().size());
    }
    @Test void nearbyConnectionsAndColocatedStationCodesRemainClearlyMarked(){
        when(stations.page(0,100)).thenReturn(new PageResult<>(List.of(station("AAN",19,73)),0,100,1,1));
        route("T3",List.of(stop("AAN",1,1,"09:00",0),stop("XXX",2,1,"12:00",200)));
        assertTrue(search(true).options().stream().anyMatch(p->p.kind().equals("NEARBY_CONNECTION")&&p.originDistanceKm()==0));
    }
    @Test void missingCoordinatesExplainLimitationButExactConnectionsRemain(){
        when(stations.station("AAA")).thenReturn(new CatalogStation("AAA","AAA",null,null));var r=search(true);
        assertEquals(1,r.options().size());assertTrue(r.notices().stream().anyMatch(s->s.contains("AAA")&&s.contains("coordinates")));
    }
    @Test void rejectsLoopsAndDoesNotSplitSameTrainIntoFakeConnection(){
        route("T2",List.of(stop("XXX",1,1,"13:00",0),stop("AAA",2,1,"14:00",100),stop("CCC",3,1,"15:00",200)));
        assertTrue(search(false).options().isEmpty());
        catalogue.clear();route("T1",List.of(stop("AAA",1,1,"10:00",0),stop("XXX",2,1,"12:00",100),stop("CCC",3,1,"15:00",200)));assertTrue(search(false).options().isEmpty());
    }
    @Test void waitlistOnEitherLegLabelsWholeSuggestionAndUnavailableLegIsRemoved(){
        doAnswer(i->{JourneyQuote q=i.getArgument(0);q.setSuggestedAllocation(q.getTrainNumber().equals("T2")?"WL":"CNF");return null;}).when(engine).describeAvailability(any());assertEquals("WL",search(false).options().getFirst().allocation());
        doAnswer(i->{JourneyQuote q=i.getArgument(0);q.setSuggestedAllocation("UNAVAILABLE");return null;}).when(engine).describeAvailability(any());assertTrue(search(false).options().isEmpty());
    }
    @Test void secondLegCannotExceedBookingWindow(){
        route("T1",List.of(stop("AAA",1,1,"22:00",0),stop("XXX",2,1,"23:30",100)));
        route("T2",List.of(stop("XXX",1,1,"00:45",0),stop("CCC",2,1,"03:00",100)));
        assertTrue(planner.search("AAA","CCC",LocalDate.of(2030,3,1),"SL",1,false,50,60,360).options().isEmpty());
    }
    @Test void invalidInputsDoNotReadRemoteCatalogue(){
        assertThrows(BookingException.class,()->planner.search("AAA","AAA",date,"SL",1,false,50,60,360));
        assertThrows(BookingException.class,()->planner.search("AAA","CCC",date,"INVALID",1,false,50,60,360));
        assertThrows(BookingException.class,()->planner.search("AAA","CCC",date,"SL",-1,false,50,60,360));
        assertThrows(BookingException.class,()->planner.search("AAA","CCC",date,"SL",1,false,101,60,360));
        assertThrows(BookingException.class,()->planner.search("AAA","CCC",date,"SL",1,false,50,15,360));
        assertThrows(BookingException.class,()->planner.search("AAA","CCC",LocalDate.of(2029,12,31),"SL",1,false,50,60,360));
        verifyNoInteractions(stations);
    }
}
